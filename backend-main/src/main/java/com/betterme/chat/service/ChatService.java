package com.betterme.chat.service;

import com.betterme.chat.model.*;
import com.betterme.chat.repository.*;
import com.betterme.social.repository.FriendshipRepository;
import com.betterme.social.service.PresenceService;
import com.betterme.user.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.betterme.chat.dto.ChatViews.*;

@Service
@Transactional(readOnly = true)
public class ChatService {
    private final UserRepository users;
    private final FriendshipRepository friends;
    private final ChatFriendRepository friendSearch;
    private final ChatConversationRepository conversations;
    private final ChatMemberRepository members;
    private final ChatMessageRepository messages;
    private final PresenceService presence;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public ChatService(UserRepository users, FriendshipRepository friends, ChatFriendRepository friendSearch,
                       ChatConversationRepository conversations, ChatMemberRepository members,
                       ChatMessageRepository messages, PresenceService presence, EntityManager entityManager,
                       ApplicationEventPublisher events) {
        this.users = users; this.friends = friends; this.friendSearch = friendSearch;
        this.conversations = conversations; this.members = members; this.messages = messages;
        this.presence = presence; this.entityManager = entityManager; this.events = events;
    }

    public Long viewerId(String email) { return user(email).getId(); }

    public PageView<Person> friends(String email, String query, int page) {
        User viewer = user(email);
        String name = query == null ? "" : query.strip();
        if (name.length() > 80) throw bad("Search using no more than 80 characters.");
        String pattern = "%" + name.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var found = friendSearch.search(viewer.getId(), pattern, paging(page));
        Set<String> online = presence.onlineEmails();
        return new PageView<>(found.stream().map(u -> person(u, online)).toList(), found.hasNext());
    }

    public PageView<Conversation> conversations(String email, String filter, int page) {
        User viewer = user(email);
        if (!Set.of("all", "unread", "groups").contains(filter)) throw bad("Choose All, Unread, or Groups.");
        var found = members.conversations(viewer.getId(), filter.equals("groups"), filter.equals("unread"), paging(page));
        Set<String> online = presence.onlineEmails();
        return new PageView<>(found.stream().map(member -> view(member, online)).toList(), found.hasNext());
    }

    public Details details(String email, Long id) {
        User viewer = user(email);
        ChatMember member = access(viewer, id, false);
        Set<String> online = presence.onlineEmails();
        return new Details(view(member, online), members.findByConversationIdOrderByIdAsc(id).stream()
                .map(m -> person(m.getUser(), online)).toList());
    }

    @Transactional
    public Details direct(String email, Long friendId) {
        User viewer = user(email);
        if (friendId == null || friendId.equals(viewer.getId())) throw bad("Choose a friend to message.");
        long low = Math.min(viewer.getId(), friendId), high = Math.max(viewer.getId(), friendId);
        User lowUser = entityManager.find(User.class, low, LockModeType.PESSIMISTIC_WRITE);
        User highUser = entityManager.find(User.class, high, LockModeType.PESSIMISTIC_WRITE);
        if (lowUser == null || highUser == null) throw missing();
        requireFriend(viewer.getId(), friendId);
        ChatConversation conversation = conversations.findByDirectLowIdAndDirectHighId(low, high).orElse(null);
        if (conversation == null) {
            conversation = new ChatConversation();
            conversation.setCreator(viewer);
            conversation.setDirectLow(lowUser); conversation.setDirectHigh(highUser);
            conversations.save(conversation);
            members.save(new ChatMember(conversation, lowUser));
            members.save(new ChatMember(conversation, highUser));
            changed(conversation.getId(), Set.of(low, high));
        }
        return details(email, conversation.getId());
    }

    @Transactional
    public Details group(String email, String name, List<Long> friendIds) {
        User viewer = user(email);
        String title = text(name, 80, "Give your group a name.");
        if (friendIds == null || friendIds.size() > 49 || friendIds.stream().anyMatch(Objects::isNull))
            throw bad("Choose between 2 and 49 friends.");
        Set<Long> selected = new LinkedHashSet<>(friendIds);
        selected.remove(viewer.getId());
        if (selected.size() < 2) throw bad("Choose at least two friends for a group.");
        List<User> participants = new ArrayList<>(); participants.add(viewer);
        for (Long friendId : selected) {
            requireFriend(viewer.getId(), friendId);
            participants.add(users.findById(friendId).orElseThrow(this::missing));
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setGroupChat(true); conversation.setName(title); conversation.setCreator(viewer);
        conversations.save(conversation);
        for (User participant : participants) members.save(new ChatMember(conversation, participant));
        Set<Long> recipients = new HashSet<>(selected); recipients.add(viewer.getId());
        changed(conversation.getId(), recipients);
        return details(email, conversation.getId());
    }

    public PageView<Message> history(String email, Long id, Long before, Long after) {
        access(user(email), id, false);
        if ((before != null && before <= 0) || (after != null && after < 0) || (before != null && after != null))
            throw bad("Choose a valid message position.");
        var found = after == null ? messages.history(id, before, PageRequest.of(0, 30))
                : messages.newer(id, after, PageRequest.of(0, 30));
        List<Message> result = new ArrayList<>(found.stream().map(this::message).toList());
        if (after == null) Collections.reverse(result);
        return new PageView<>(result, found.hasNext());
    }

    @Transactional
    public Message send(String email, Long id, String text, UUID clientId) {
        User viewer = user(email);
        ChatMember member = access(viewer, id, true);
        String body = text(text, 2000, "Write a message first.");
        if (clientId == null) throw bad("Refresh chat and try sending again.");
        ChatMessage existing = messages.findBySenderIdAndClientId(viewer.getId(), clientId).orElse(null);
        if (existing != null) {
            if (!existing.getConversation().getId().equals(id) || !existing.getText().equals(body))
                throw bad("This message reference has already been used.");
            return message(existing);
        }
        ChatMessage message = messages.saveAndFlush(new ChatMessage(member.getConversation(), viewer, body, clientId));
        member.getConversation().recordMessage(message);
        changed(id, members.memberIds(id));
        return message(message);
    }

    @Transactional
    public Read read(String email, Long id, long throughId) {
        User viewer = user(email);
        ChatMember member = access(viewer, id, true);
        if (throughId <= 0 || !messages.existsByIdAndConversationId(throughId, id)) throw bad("Choose a message in this conversation.");
        if (throughId > member.getLastReadMessageId()) {
            member.readThrough(throughId);
            changed(id, Set.of(viewer.getId()));
        }
        return new Read(messages.unread(id, viewer.getId(), member.getLastReadMessageId()));
    }

    public long unread(String email) { return messages.totalUnread(user(email).getId()); }

    private ChatMember access(User viewer, Long id, boolean lock) {
        if (lock) conversations.lockById(id).orElseThrow(this::missing);
        ChatMember member = members.findByConversationIdAndUserId(id, viewer.getId()).orElseThrow(this::missing);
        ChatConversation conversation = member.getConversation();
        if (!conversation.isGroupChat()) {
            Long other = conversation.getDirectLow().getId().equals(viewer.getId())
                    ? conversation.getDirectHigh().getId() : conversation.getDirectLow().getId();
            requireFriend(viewer.getId(), other);
        }
        return member;
    }

    private Conversation view(ChatMember member, Set<String> online) {
        ChatConversation c = member.getConversation();
        User other = c.isGroupChat() ? null : c.getDirectLow().getId().equals(member.getUser().getId()) ? c.getDirectHigh() : c.getDirectLow();
        return new Conversation(c.getId(), other == null ? c.getName() : other.getFullName(), c.isGroupChat(),
                members.memberIds(c.getId()).size(), c.getLastMessageText(),
                c.getLastSender() == null ? null : c.getLastSender().getFullName(),
                c.getLastSender() == null ? null : c.getLastSender().getId(), c.getUpdatedAt(),
                messages.unread(c.getId(), member.getUser().getId(), member.getLastReadMessageId()),
                other != null && online.contains(other.getEmail()));
    }

    private Message message(ChatMessage m) {
        return new Message(m.getId(), m.getConversation().getId(), m.getSender().getId(),
                m.getSender().getFullName(), m.getText(), m.getCreatedAt(), m.getClientId());
    }
    private Person person(User user, Set<String> online) { return new Person(user.getId(), user.getFullName(), online.contains(user.getEmail())); }
    private void requireFriend(Long a, Long b) {
        if (friends.findByLowUserIdAndHighUserId(Math.min(a, b), Math.max(a, b)).filter(f -> f.isAccepted()).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only start a chat with an accepted friend.");
    }
    private void changed(Long id, Set<Long> recipients) { events.publishEvent(new ChatChanged(Set.copyOf(recipients), id)); }
    private User user(String email) { return users.findByEmail(email).orElseThrow(this::missing); }
    private PageRequest paging(int page) { return PageRequest.of(Math.max(0, page), 20); }
    private String text(String value, int max, String empty) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isBlank()) throw bad(empty);
        if (cleaned.length() > max) throw bad("Keep this under " + max + " characters.");
        return cleaned;
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation or message unavailable to you."); }
}

package com.betterme.chat.repository;

import com.betterme.chat.model.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findBySenderIdAndClientId(Long senderId, UUID clientId);
    boolean existsByIdAndConversationId(Long id, Long conversationId);

    @EntityGraph(attributePaths = "sender")
    @Query("select m from ChatMessage m where m.conversation.id = :id and (:before is null or m.id < :before) order by m.id desc")
    Slice<ChatMessage> history(Long id, Long before, Pageable pageable);

    @EntityGraph(attributePaths = "sender")
    @Query("select m from ChatMessage m where m.conversation.id = :id and m.id > :after order by m.id asc")
    Slice<ChatMessage> newer(Long id, Long after, Pageable pageable);

    @Query("select count(m) from ChatMessage m where m.conversation.id = :id and m.sender.id <> :viewer and m.id > :readThrough")
    long unread(Long id, Long viewer, long readThrough);

    @Query("""
        select count(msg) from ChatMessage msg where msg.sender.id <> :viewer and exists (
          select cm.id from ChatMember cm where cm.user.id = :viewer
          and cm.conversation = msg.conversation and msg.id > cm.lastReadMessageId
          and (cm.conversation.groupChat = true or exists (select f.id from Friendship f
            where f.accepted = true and f.lowUser = cm.conversation.directLow and f.highUser = cm.conversation.directHigh)))
        """)
    long totalUnread(Long viewer);
}

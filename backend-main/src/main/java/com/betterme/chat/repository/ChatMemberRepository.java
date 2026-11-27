package com.betterme.chat.repository;

import com.betterme.chat.model.ChatMember;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
    Optional<ChatMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<ChatMember> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("select m.user.id from ChatMember m where m.conversation.id = :id")
    Set<Long> memberIds(Long id);

    @EntityGraph(attributePaths = {"conversation", "conversation.directLow", "conversation.directHigh", "conversation.lastSender"})
    @Query("""
        select m from ChatMember m where m.user.id = :viewer
        and (m.conversation.groupChat = true or exists (select f.id from Friendship f
          where f.accepted = true and f.lowUser = m.conversation.directLow and f.highUser = m.conversation.directHigh))
        and (:groupsOnly = false or m.conversation.groupChat = true)
        and (:unreadOnly = false or exists (select msg.id from ChatMessage msg
          where msg.conversation = m.conversation and msg.id > m.lastReadMessageId and msg.sender.id <> :viewer))
        order by m.conversation.updatedAt desc, m.conversation.id desc
        """)
    Slice<ChatMember> conversations(Long viewer, boolean groupsOnly, boolean unreadOnly, Pageable pageable);
}

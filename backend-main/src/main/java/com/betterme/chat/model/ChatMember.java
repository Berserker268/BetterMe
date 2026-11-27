package com.betterme.chat.model;

import com.betterme.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "chat_members", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_member", columnNames = {"conversation_id", "user_id"}),
        indexes = @Index(name = "idx_chat_member_user", columnList = "user_id"))
public class ChatMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversation_id")
    private ChatConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false)
    private long lastReadMessageId;

    public ChatMember() {}
    public ChatMember(ChatConversation conversation, User user) {
        this.conversation = conversation;
        this.user = user;
    }
    public ChatConversation getConversation() { return conversation; }
    public User getUser() { return user; }
    public long getLastReadMessageId() { return lastReadMessageId; }
    public void readThrough(long id) { lastReadMessageId = Math.max(lastReadMessageId, id); }
}

package com.betterme.chat.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_message_retry", columnNames = {"sender_id", "client_id"}),
        indexes = @Index(name = "idx_chat_message_conversation", columnList = "conversation_id,id"))
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversation_id")
    private ChatConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sender_id")
    private User sender;
    @Column(nullable = false, length = 2000)
    private String text;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public ChatMessage() {}
    public ChatMessage(ChatConversation conversation, User sender, String text, UUID clientId) {
        this.conversation = conversation;
        this.sender = sender;
        this.text = text;
        this.clientId = clientId;
    }
    public Long getId() { return id; }
    public ChatConversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public String getText() { return text; }
    public UUID getClientId() { return clientId; }
    public Instant getCreatedAt() { return createdAt; }
}

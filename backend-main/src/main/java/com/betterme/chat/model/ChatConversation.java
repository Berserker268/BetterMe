package com.betterme.chat.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_conversations", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_direct_pair", columnNames = {"direct_low_id", "direct_high_id"}))
public class ChatConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private boolean groupChat;
    @Column(length = 80)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creator_id")
    private User creator;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "direct_low_id")
    private User directLow;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "direct_high_id")
    private User directHigh;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    private Long lastMessageId;
    @Column(length = 240)
    private String lastMessageText;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "last_sender_id")
    private User lastSender;

    public ChatConversation() {}
    public Long getId() { return id; }
    public boolean isGroupChat() { return groupChat; }
    public void setGroupChat(boolean value) { groupChat = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public User getCreator() { return creator; }
    public void setCreator(User value) { creator = value; }
    public User getDirectLow() { return directLow; }
    public void setDirectLow(User value) { directLow = value; }
    public User getDirectHigh() { return directHigh; }
    public void setDirectHigh(User value) { directHigh = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getLastMessageId() { return lastMessageId; }
    public String getLastMessageText() { return lastMessageText; }
    public User getLastSender() { return lastSender; }
    public void recordMessage(ChatMessage message) {
        lastMessageId = message.getId();
        lastMessageText = message.getText().substring(0, Math.min(240, message.getText().length()));
        lastSender = message.getSender();
        updatedAt = message.getCreatedAt();
    }
}

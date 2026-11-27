package com.betterme.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatViews {
    private ChatViews() {}
    public record Person(Long id, String name, boolean online) {}
    public record Conversation(Long id, String name, boolean groupChat, int memberCount,
                               String lastMessage, String lastSender, Long lastSenderId,
                               Instant updatedAt, long unread, boolean online) {}
    public record Details(Conversation conversation, List<Person> members) {}
    public record Message(Long id, Long conversationId, Long senderId, String senderName,
                          String text, Instant createdAt, UUID clientId) {}
    public record PageView<T>(List<T> items, boolean hasMore) {}
    public record Read(long unread) {}
}

package com.betterme.chat.service;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class ChatStreamService {
    private record Connection(SseEmitter emitter, HttpSession session) {}
    private final ConcurrentMap<Long, CopyOnWriteArraySet<Connection>> clients = new ConcurrentHashMap<>();
    private final SessionRegistry sessions;
    private final ScheduledExecutorService heartbeat;

    public ChatStreamService(SessionRegistry sessions) {
        this.sessions = sessions;
        heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "betterme-chat-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(this::ping, 15, 15, TimeUnit.SECONDS);
    }

    public SseEmitter connect(Long viewer, HttpSession session) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Connection connection = new Connection(emitter, session);
        java.util.List<Connection> evicted = new java.util.ArrayList<>();
        clients.compute(viewer, (id, existing) -> {
            var connections = existing == null ? new CopyOnWriteArraySet<Connection>() : existing;
            if (connections.size() >= 8) {
                Connection oldest = connections.iterator().next();
                connections.remove(oldest);
                evicted.add(oldest);
            }
            connections.add(connection);
            return connections;
        });
        evicted.forEach(old -> old.emitter().complete());
        emitter.onCompletion(() -> remove(viewer, connection));
        emitter.onTimeout(() -> { remove(viewer, connection); emitter.complete(); });
        emitter.onError(error -> remove(viewer, connection));
        send(viewer, connection, "ready", "connected");
        return emitter;
    }

    // Notify only after the message/read transaction has committed successfully.
    @TransactionalEventListener
    public void changed(ChatChanged event) {
        for (Long viewer : event.recipients()) {
            Set<Connection> connections = clients.get(viewer);
            if (connections != null) for (Connection connection : connections) {
                send(viewer, connection, "chat-changed", event.conversationId().toString());
            }
        }
    }

    private void ping() {
        clients.forEach((viewer, connections) -> connections.forEach(connection -> send(viewer, connection, "ping", "")));
    }

    private void send(Long viewer, Connection connection, String event, String data) {
        try {
            String sessionId = connection.session().getId();
            connection.session().getCreationTime(); // Throws if logout invalidated this session.
            var information = sessions.getSessionInformation(sessionId);
            if (information != null && information.isExpired()) {
                remove(viewer, connection);
                connection.emitter().complete();
                return;
            }
            connection.emitter().send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException exception) {
            remove(viewer, connection);
            connection.emitter().complete();
        }
    }

    private void remove(Long viewer, Connection connection) {
        clients.computeIfPresent(viewer, (id, connections) -> {
            connections.remove(connection);
            return connections.isEmpty() ? null : connections;
        });
    }

    @PreDestroy
    public void close() {
        heartbeat.shutdownNow();
        clients.values().forEach(connections -> connections.forEach(connection -> connection.emitter().complete()));
        clients.clear();
    }
}

package com.betterme.chat.controller;

import com.betterme.chat.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import static com.betterme.chat.dto.ChatViews.*;

@RestController
@RequestMapping("/chat/api")
public class ChatApiController {
    private final ChatService chat;
    private final ChatStreamService stream;
    public ChatApiController(ChatService chat, ChatStreamService stream) { this.chat = chat; this.stream = stream; }
    public record DirectInput(Long friendId) {}
    public record GroupInput(String name, List<Long> friendIds) {}
    public record MessageInput(String text, UUID clientId) {}
    public record ReadInput(long throughId) {}

    @GetMapping("/friends")
    public PageView<Person> friends(Authentication auth, @RequestParam(name = "q", defaultValue = "") String query,
                                    @RequestParam(name = "page", defaultValue = "0") int page) {
        return chat.friends(auth.getName(), query, page);
    }
    @GetMapping("/conversations")
    public PageView<Conversation> conversations(Authentication auth, @RequestParam(name = "filter", defaultValue = "all") String filter,
                                               @RequestParam(name = "page", defaultValue = "0") int page) {
        return chat.conversations(auth.getName(), filter, page);
    }
    @PostMapping("/direct")
    public Details direct(Authentication auth, @RequestBody DirectInput input) { return chat.direct(auth.getName(), input.friendId()); }
    @PostMapping("/groups")
    public Details group(Authentication auth, @RequestBody GroupInput input) { return chat.group(auth.getName(), input.name(), input.friendIds()); }
    @GetMapping("/conversations/{id}")
    public Details details(Authentication auth, @PathVariable("id") Long id) { return chat.details(auth.getName(), id); }
    @GetMapping("/conversations/{id}/messages")
    public PageView<Message> history(Authentication auth, @PathVariable("id") Long id,
                                     @RequestParam(name = "before", required = false) Long before,
                                     @RequestParam(name = "after", required = false) Long after) {
        return chat.history(auth.getName(), id, before, after);
    }
    @PostMapping("/conversations/{id}/messages")
    public Message send(Authentication auth, @PathVariable("id") Long id, @RequestBody MessageInput input) {
        return chat.send(auth.getName(), id, input.text(), input.clientId());
    }
    @PutMapping("/conversations/{id}/read")
    public Read read(Authentication auth, @PathVariable("id") Long id, @RequestBody ReadInput input) {
        return chat.read(auth.getName(), id, input.throughId());
    }
    @GetMapping("/unread")
    public Read unread(Authentication auth) { return new Read(chat.unread(auth.getName())); }
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(Authentication auth, HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
                .body(stream.connect(chat.viewerId(auth.getName()), session));
    }
}

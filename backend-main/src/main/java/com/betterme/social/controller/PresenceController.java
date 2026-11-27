package com.betterme.social.controller;

import com.betterme.social.service.PresenceService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PresenceController {
    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @PostMapping("/social/api/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(Authentication authentication, HttpSession session) {
        presence.heartbeat(authentication, session.getId());
    }
}

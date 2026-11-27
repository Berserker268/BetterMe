package com.betterme.social.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
public class PresenceService {
    private final SessionRegistry sessions;

    public PresenceService(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public void heartbeat(Authentication authentication, String sessionId) {
        if (sessions.getSessionInformation(sessionId) == null) {
            sessions.registerNewSession(sessionId, authentication.getPrincipal());
        } else {
            sessions.refreshLastRequest(sessionId);
        }
    }

    public Set<String> onlineEmails() {
        Instant cutoff = Instant.now().minusSeconds(120);
        Set<String> online = new HashSet<>();
        for (Object principal : sessions.getAllPrincipals()) {
            String email = null;
            if (principal instanceof UserDetails user) email = user.getUsername();
            else if (principal instanceof OAuth2User user) email = user.getAttribute("email");
            if (email != null && sessions.getAllSessions(principal, false).stream()
                    .anyMatch(session -> session.getLastRequest().toInstant().isAfter(cutoff))) {
                online.add(email);
            }
        }
        return online;
    }
}

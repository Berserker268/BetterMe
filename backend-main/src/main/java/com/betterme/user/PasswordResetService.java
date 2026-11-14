package com.betterme.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int MAX_REQUESTS_PER_HOUR = 3;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SessionRegistry sessionRegistry;

    @Value("${app.base-url}")
    private String baseUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                JavaMailSender mailSender,
                                SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.sessionRegistry = sessionRegistry;
    }

    public void sendResetLink(String email) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();

        long recent = tokenRepository.countByUserIdAndCreatedAtAfter(
                user.getId(), LocalDateTime.now().minusHours(1));
        if (recent >= MAX_REQUESTS_PER_HOUR) {
            return;
        }

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(rawToken));
        token.setUserId(user.getId());
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        tokenRepository.save(token);

        String link = baseUrl + "/reset-password?token=" + rawToken;

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(user.getEmail());
            msg.setSubject("Reset your BetterMe password");
            msg.setText("Hi " + user.getFullName() + ",\n\n"
                    + "Click this link to choose a new password (valid for 30 minutes):\n"
                    + link + "\n\n"
                    + "If you did not ask for this, you can ignore this email.");
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Could not send email ({}). Dev reset link: {}", e.getMessage(), link);
        }
    }

    public boolean isValid(String rawToken) {
        return findUsable(rawToken).isPresent();
    }

    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        Optional<PasswordResetToken> found = findUsable(rawToken);
        if (found.isEmpty()) {
            return false;
        }
        PasswordResetToken token = found.get();
        Optional<User> user = userRepository.findById(token.getUserId());
        if (user.isEmpty()) {
            return false;
        }
        user.get().setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user.get());
        token.setUsed(true);
        tokenRepository.save(token);

        logOutEverywhere(user.get().getEmail());
        return true;
    }

    private void logOutEverywhere(String email) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String name = null;
            if (principal instanceof UserDetails details) {
                name = details.getUsername();
            } else if (principal instanceof AuthenticatedPrincipal authenticated) {
                name = authenticated.getName();
            }
            if (email.equalsIgnoreCase(name)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }

    private Optional<PasswordResetToken> findUsable(String rawToken) {
        return tokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
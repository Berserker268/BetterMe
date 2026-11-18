package com.betterme.social.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "social_friendships", uniqueConstraints = @UniqueConstraint(
        name = "uk_social_friend_pair", columnNames = {"low_user_id", "high_user_id"}))
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "low_user_id")
    private User lowUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "high_user_id")
    private User highUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requester_id")
    private User requester;
    @Column(nullable = false)
    private boolean accepted;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Friendship() {}

    public Long getId() { return id; }
    public User getLowUser() { return lowUser; }
    public void setLowUser(User lowUser) { this.lowUser = lowUser; }
    public User getHighUser() { return highUser; }
    public void setHighUser(User highUser) { this.highUser = highUser; }
    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }
    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public Instant getCreatedAt() { return createdAt; }
}

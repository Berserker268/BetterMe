package com.betterme.social.model;

import com.betterme.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "social_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_social_like", columnNames = {"post_id", "user_id"}))
public class SocialLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id")
    private SocialPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;

    public SocialLike() {}

    public Long getId() { return id; }
    public SocialPost getPost() { return post; }
    public void setPost(SocialPost post) { this.post = post; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}

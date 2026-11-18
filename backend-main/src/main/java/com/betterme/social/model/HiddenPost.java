package com.betterme.social.model;

import com.betterme.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "social_hidden_posts", uniqueConstraints = @UniqueConstraint(
        name = "uk_social_hidden_post", columnNames = {"post_id", "user_id"}))
public class HiddenPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id")
    private SocialPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;

    public HiddenPost() {}

    public HiddenPost(SocialPost post, User user) {
        this.post = post;
        this.user = user;
    }
}

package com.betterme.social.model;

import com.betterme.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "social_comment_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_social_comment_like", columnNames = {"comment_id", "user_id"}))
public class CommentLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "comment_id")
    private SocialComment comment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;

    public CommentLike() {}

    public CommentLike(SocialComment comment, User user) {
        this.comment = comment;
        this.user = user;
    }
}

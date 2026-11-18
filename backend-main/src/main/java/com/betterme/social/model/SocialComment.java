package com.betterme.social.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "social_comments", indexes = @Index(name = "idx_social_comment_post", columnList = "post_id,created_at"))
public class SocialComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id")
    private SocialPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id")
    private User author;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private SocialComment parent;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private SocialComment replyTo;
    @Column(nullable = false, length = 1000)
    private String text;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SocialComment() {}

    public Long getId() { return id; }
    public SocialPost getPost() { return post; }
    public void setPost(SocialPost post) { this.post = post; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public SocialComment getParent() { return parent; }
    public void setParent(SocialComment parent) { this.parent = parent; }
    public SocialComment getReplyTo() { return replyTo; }
    public void setReplyTo(SocialComment replyTo) { this.replyTo = replyTo; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
}

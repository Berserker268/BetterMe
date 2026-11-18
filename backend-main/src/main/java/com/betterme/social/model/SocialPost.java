package com.betterme.social.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "social_posts", indexes = @Index(name = "idx_social_post_author_date", columnList = "author_id,created_at"))
public class SocialPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id")
    private User author;
    @Column(nullable = false, length = 2000)
    private String text;
    @Column(length = 20)
    private String category;
    @Column(nullable = false)
    private boolean hasPhoto;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SocialPost() {}

    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean hasPhoto() { return hasPhoto; }
    public void setHasPhoto(boolean hasPhoto) { this.hasPhoto = hasPhoto; }
    public Instant getCreatedAt() { return createdAt; }
}

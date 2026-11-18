package com.betterme.social.model;

import jakarta.persistence.*;

@Entity
@Table(name = "social_photos")
public class SocialPhoto {
    @Id
    private Long postId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "post_id")
    private SocialPost post;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    public SocialPhoto() {}

    public Long getPostId() { return postId; }
    public SocialPost getPost() { return post; }
    public void setPost(SocialPost post) { this.post = post; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
}

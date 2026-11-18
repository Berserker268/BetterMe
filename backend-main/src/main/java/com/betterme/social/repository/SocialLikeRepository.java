package com.betterme.social.repository;

import com.betterme.social.model.SocialLike;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface SocialLikeRepository extends JpaRepository<SocialLike, Long> {
    @Modifying
    @Query("delete from SocialLike l where l.post.id = :postId")
    void deleteAllForPost(Long postId);

    Optional<SocialLike> findByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);
}

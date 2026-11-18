package com.betterme.social.repository;

import com.betterme.social.model.SocialPhoto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SocialPhotoRepository extends JpaRepository<SocialPhoto, Long> {
    @Modifying
    @Query("delete from SocialPhoto p where p.postId = :postId")
    void deleteAllForPost(Long postId);
}

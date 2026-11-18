package com.betterme.social.repository;

import com.betterme.social.model.HiddenPost;
import org.springframework.data.jpa.repository.*;

public interface HiddenPostRepository extends JpaRepository<HiddenPost, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @Modifying
    @Query("delete from HiddenPost h where h.post.id = :postId")
    void deleteAllForPost(Long postId);
}

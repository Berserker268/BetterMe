package com.betterme.social.repository;

import com.betterme.social.model.CommentLike;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);
    long countByCommentId(Long commentId);
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    @Modifying
    @Query("delete from CommentLike l where l.comment.id in (select c.id from SocialComment c where c.id = :commentId or c.parent.id = :commentId)")
    void deleteForCommentThread(Long commentId);

    @Modifying
    @Query("delete from CommentLike l where l.comment.id in (select c.id from SocialComment c where c.post.id = :postId)")
    void deleteAllForPost(Long postId);
}

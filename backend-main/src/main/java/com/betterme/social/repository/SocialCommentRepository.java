package com.betterme.social.repository;

import com.betterme.social.model.SocialComment;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SocialCommentRepository extends JpaRepository<SocialComment, Long> {
    @Modifying
    @Query("""
        update SocialComment c set c.replyTo = null where c.post.id = :postId and
        (c.replyTo.id = :commentId or c.replyTo.id in
          (select r.id from SocialComment r where r.parent.id = :commentId))
        """)
    void clearReplyTargets(Long postId, Long commentId);

    @Modifying
    @Query("delete from SocialComment c where c.id = :commentId or c.parent.id = :commentId")
    int deleteCommentThread(Long commentId);

    @Modifying
    @Query("delete from SocialComment c where c.post.id = :postId")
    void deleteAllForPost(Long postId);

    @EntityGraph(attributePaths = "author")
    Page<SocialComment> findByPostIdAndParentIsNull(Long postId, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "replyTo.author"})
    Page<SocialComment> findByPostIdAndParentId(Long postId, Long parentId, Pageable pageable);
    long countByParentId(Long parentId);
    long countByPostId(Long postId);
}

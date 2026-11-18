package com.betterme.social.repository;

import com.betterme.social.model.Friendship;
import com.betterme.social.model.SocialPost;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
    @EntityGraph(attributePaths = "author")
    @Query("""
        select p from SocialPost p where (p.author.id = :viewer or exists (
          select f.id from Friendship f where f.accepted = true and
          ((f.lowUser.id = :viewer and f.highUser.id = p.author.id) or
           (f.highUser.id = :viewer and f.lowUser.id = p.author.id))))
        and not exists (select h.id from HiddenPost h where h.post.id = p.id and h.user.id = :viewer)
        """)
    Page<SocialPost> feed(Long viewer, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SocialPost p where p.id = :id")
    Optional<SocialPost> lockById(Long id);
}

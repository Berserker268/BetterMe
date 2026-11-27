package com.betterme.social.repository;

import com.betterme.social.model.Friendship;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query("""
        select case when f.lowUser.id = :viewer then f.highUser.id else f.lowUser.id end
        from Friendship f where f.accepted = true and
        ((f.lowUser.id = :viewer and f.highUser.email in :emails) or
         (f.highUser.id = :viewer and f.lowUser.email in :emails))
        """)
    List<Long> onlineFriends(Long viewer, Set<String> emails);

    Optional<Friendship> findByLowUserIdAndHighUserId(Long low, Long high);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Friendship f where f.id = :id")
    Optional<Friendship> lockById(Long id);

    @EntityGraph(attributePaths = {"lowUser", "highUser", "requester"})
    @Query("select f from Friendship f where (f.lowUser.id = :id or f.highUser.id = :id) and f.accepted = true")
    Page<Friendship> friends(Long id, Pageable pageable);

    @EntityGraph(attributePaths = {"lowUser", "highUser", "requester"})
    @Query("select f from Friendship f where (f.lowUser.id = :id or f.highUser.id = :id) and f.requester.id <> :id and f.accepted = false")
    Page<Friendship> incoming(Long id, Pageable pageable);
}

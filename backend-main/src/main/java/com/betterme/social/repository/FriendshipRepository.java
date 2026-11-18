package com.betterme.social.repository;

import com.betterme.social.model.Friendship;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
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

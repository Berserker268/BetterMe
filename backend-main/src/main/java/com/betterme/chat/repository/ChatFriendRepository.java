package com.betterme.chat.repository;

import com.betterme.user.User;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ChatFriendRepository extends JpaRepository<User, Long> {
    @Query("""
        select u from User u where lower(u.fullName) like lower(:pattern) escape '!' and exists (
          select f.id from Friendship f where f.accepted = true and
          ((f.lowUser.id = :viewer and f.highUser.id = u.id) or
           (f.highUser.id = :viewer and f.lowUser.id = u.id)))
        order by u.fullName, u.id
        """)
    Slice<User> search(Long viewer, String pattern, Pageable pageable);
}

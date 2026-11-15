
package com.betterme.goal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.betterme.goal.model.Goal;
import com.betterme.goal.model.GoalStatus;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

   List<Goal> findByUserId(Long userId);

   Optional<Goal> findByIdAndUserId(Long id, Long userId);

   List<Goal> findByUserIdAndStatus(Long userId, GoalStatus status);

   List<Goal> findByUserIdAndTitleContainingIgnoreCase(
         Long userId, String title);
}

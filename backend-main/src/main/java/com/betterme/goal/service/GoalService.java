
package com.betterme.goal.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.betterme.goal.dto.GoalRequest;
import com.betterme.goal.dto.GoalResponse;
import com.betterme.goal.model.Goal;
import com.betterme.goal.model.GoalStatus;
import com.betterme.goal.repository.GoalRepository;
import com.betterme.user.User;
import com.betterme.user.UserRepository;

@Service
public class GoalService {

   private final GoalRepository goalRepository;
   private final UserRepository userRepository;

   public GoalService(GoalRepository goalRepository,
                     UserRepository userRepository) {
      this.goalRepository = goalRepository;
      this.userRepository = userRepository;
   }

   // Get all goals
   public List<GoalResponse> getAllGoals(String email) {
      User user = getUser(email);

      return goalRepository.findByUserId(user.getId())
               .stream()
               .map(this::convertToResponse)
               .toList();
   }

   // Get goals by status
   public List<GoalResponse> getGoalsByStatus(
         String email, GoalStatus status) {

      User user = getUser(email);

      return goalRepository.findByUserIdAndStatus(user.getId(), status)
               .stream()
               .map(this::convertToResponse)
               .toList();
   }

   // Search goals
   public List<GoalResponse> searchGoals(
         String email, String keyword) {

      User user = getUser(email);

      return goalRepository
               .findByUserIdAndTitleContainingIgnoreCase(
                     user.getId(), keyword
               )
               .stream()
               .map(this::convertToResponse)
               .toList();
   }

   // Create goal
   public GoalResponse createGoal(GoalRequest request, String email) {
      User user = getUser(email);

      Goal goal = new Goal();

      goal.setTitle(request.getTitle());
      goal.setDescription(request.getDescription());
      goal.setDeadline(request.getDeadline());
      goal.setProgress(0);
      goal.setStatus(GoalStatus.NOT_STARTED);
      goal.setUser(user);

      Goal savedGoal = goalRepository.save(goal);

      return convertToResponse(savedGoal);
   }

   // Update goal
   public GoalResponse updateGoal(
         Long goalId, GoalRequest request, String email) {

      User user = getUser(email);

      Goal goal = goalRepository.findByIdAndUserId(
               goalId, user.getId()
      ).orElseThrow(() -> new RuntimeException("Goal not found"));

      goal.setTitle(request.getTitle());
      goal.setDescription(request.getDescription());
      goal.setDeadline(request.getDeadline());

      if (request.getProgress() != null) {
         goal.setProgress(request.getProgress());

         if (request.getProgress() == 100) {
               goal.setStatus(GoalStatus.COMPLETED);
         } else if (request.getProgress() > 0) {
               goal.setStatus(GoalStatus.IN_PROGRESS);
         } else {
               goal.setStatus(GoalStatus.NOT_STARTED);
         }
      }

      Goal updatedGoal = goalRepository.save(goal);

      return convertToResponse(updatedGoal);
   }

   // Delete goal
   public void deleteGoal(Long goalId, String email) {
      User user = getUser(email);

      Goal goal = goalRepository.findByIdAndUserId(
               goalId, user.getId()
      ).orElseThrow(() -> new RuntimeException("Goal not found"));

      goalRepository.delete(goal);
   }

   // Find user
   private User getUser(String email) {
      return userRepository.findByEmail(email)
               .orElseThrow(() -> new RuntimeException("User not found"));
   }

   // Convert entity into response DTO
   private GoalResponse convertToResponse(Goal goal) {
      return new GoalResponse(
               goal.getId(),
               goal.getTitle(),
               goal.getDescription(),
               goal.getDeadline(),
               goal.getCreatedAt(),
               goal.getStatus(),
               goal.getProgress()
      );
   }
}

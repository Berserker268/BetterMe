
package com.betterme.goal.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.betterme.goal.dto.GoalRequest;
import com.betterme.goal.dto.GoalResponse;
import com.betterme.goal.model.GoalStatus;
import com.betterme.goal.service.GoalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

   private final GoalService goalService;

   public GoalController(GoalService goalService) {
      this.goalService = goalService;
   }

   // Get all goals
   @GetMapping
   public List<GoalResponse> getAllGoals(
         Authentication authentication) {

      return goalService.getAllGoals(authentication.getName());
   }

   // Filter by status
   @GetMapping("/status/{status}")
   public List<GoalResponse> getGoalsByStatus(
         @PathVariable GoalStatus status,
         Authentication authentication) {

      return goalService.getGoalsByStatus(
               authentication.getName(), status
      );
   }

   // Search goals
   @GetMapping("/search")
   public List<GoalResponse> searchGoals(
         @RequestParam String keyword,
         Authentication authentication) {

      return goalService.searchGoals(
               authentication.getName(), keyword
      );
   }

   // Create goal
   @PostMapping
   public GoalResponse createGoal(
         @Valid @RequestBody GoalRequest request,
         Authentication authentication) {

      return goalService.createGoal(
               request, authentication.getName()
      );
   }

   // Update goal
   @PutMapping("/{id}")
   public GoalResponse updateGoal(
         @PathVariable Long id,
         @Valid @RequestBody GoalRequest request,
         Authentication authentication) {

      return goalService.updateGoal(
               id, request, authentication.getName()
      );
   }

   // Delete goal
   @DeleteMapping("/{id}")
   public void deleteGoal(
         @PathVariable Long id,
         Authentication authentication) {

      goalService.deleteGoal(id, authentication.getName());
   }
}

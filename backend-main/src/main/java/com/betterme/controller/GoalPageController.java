
package com.betterme.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.betterme.goal.dto.GoalRequest;
import com.betterme.goal.service.GoalService;

import jakarta.validation.Valid;

@Controller
public class GoalPageController {

   private final GoalService goalService;

   public GoalPageController(GoalService goalService) {
      this.goalService = goalService;
   }

   @GetMapping("/goals")
   public String goalsPage(Authentication authentication, Model model) {
      model.addAttribute("goals",
               goalService.getAllGoals(authentication.getName()));

      model.addAttribute("goalRequest", new GoalRequest());

      return "goals";
   }

   @PostMapping("/goals/create")
   public String createGoal(
         @Valid @ModelAttribute("goalRequest") GoalRequest request,
         BindingResult result,
         Authentication authentication,
         Model model) {

      if (result.hasErrors()) {
         model.addAttribute("goals",
                  goalService.getAllGoals(authentication.getName()));
         return "goals";
      }

      goalService.createGoal(request, authentication.getName());

      return "redirect:/goals";
   }
   @PostMapping("/goals/update-progress/{id}")
   public String updateProgress(
         @PathVariable Long id,
         @RequestParam Integer progress,
         Authentication authentication) {

      GoalRequest request = new GoalRequest();

      // We only need to update the progress.
      request.setProgress(progress);

      // Keep the existing goal information.
      var goals = goalService.getAllGoals(authentication.getName());

      for (var goal : goals) {
         if (goal.getId().equals(id)) {
               request.setTitle(goal.getTitle());
               request.setDescription(goal.getDescription());
               request.setDeadline(goal.getDeadline());
               break;
         }
      }

      goalService.updateGoal(id, request, authentication.getName());

      return "redirect:/goals";
   }

   @PostMapping("/goals/delete/{id}")
   public String deleteGoal(
         @PathVariable Long id,
         Authentication authentication) {

      goalService.deleteGoal(id, authentication.getName());

      return "redirect:/goals";
   }
   @PostMapping("/goals/edit/{id}")
   public String editGoal(
         @PathVariable Long id,
         @Valid @ModelAttribute("goalRequest") GoalRequest request,
         BindingResult result,
         Authentication authentication,
         Model model) {

      if (result.hasErrors()) {
         model.addAttribute("goals",
                  goalService.getAllGoals(authentication.getName()));
         return "goals";
      }

      goalService.updateGoal(id, request, authentication.getName());

      return "redirect:/goals";
   }
}


package com.betterme.goal.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GoalRequest {

   @NotBlank(message = "Title is required")
   @Size(max = 100, message = "Title cannot exceed 100 characters")
   private String title;

   @Size(max = 1000, message = "Description cannot exceed 1000 characters")
   private String description;

   private LocalDate deadline;

   @Min(0)
   @Max(100)
   private Integer progress = 0;

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public LocalDate getDeadline() {
      return deadline;
   }

   public void setDeadline(LocalDate deadline) {
      this.deadline = deadline;
   }

   public Integer getProgress() {
      return progress;
   }

   public void setProgress(Integer progress) {
      this.progress = progress;
   }
}


package com.betterme.goal.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.betterme.goal.model.GoalStatus;

public class GoalResponse {

   private Long id;
   private String title;
   private String description;
   private LocalDate deadline;
   private LocalDateTime createdAt;
   private GoalStatus status;
   private Integer progress;

   public GoalResponse(Long id, String title, String description,
                     LocalDate deadline, LocalDateTime createdAt,
                     GoalStatus status, Integer progress) {
      this.id = id;
      this.title = title;
      this.description = description;
      this.deadline = deadline;
      this.createdAt = createdAt;
      this.status = status;
      this.progress = progress;
   }

   public Long getId() { return id; }
   public String getTitle() { return title; }
   public String getDescription() { return description; }
   public LocalDate getDeadline() { return deadline; }
   public LocalDateTime getCreatedAt() { return createdAt; }
   public GoalStatus getStatus() { return status; }
   public Integer getProgress() { return progress; }
}

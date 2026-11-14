
package com.betterme.goal.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "goals")
public class Goal {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @NotBlank(message = "Goal title is required")
   @Size(max = 100, message = "Title cannot exceed 100 characters")
   @Column(nullable = false, length = 100)
   private String title;

   @Size(max = 1000, message = "Description cannot exceed 1000 characters")
   @Column(length = 1000)
   private String description;

   private LocalDate deadline;

   @Column(nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private GoalStatus status = GoalStatus.NOT_STARTED;

   @Min(0)
   @Max(100)
   @Column(nullable = false)
   private Integer progress = 0;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "user_id", nullable = false)
   private User user;

   @PrePersist
   protected void onCreate() {
      createdAt = LocalDateTime.now();
   }

   public Long getId() {
      return id;
   }

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

   public LocalDateTime getCreatedAt() {
      return createdAt;
   }

   public GoalStatus getStatus() {
      return status;
   }

   public void setStatus(GoalStatus status) {
      this.status = status;
   }

   public Integer getProgress() {
      return progress;
   }

   public void setProgress(Integer progress) {
      this.progress = progress;
   }

   public User getUser() {
      return user;
   }

   public void setUser(User user) {
      this.user = user;
   }
}

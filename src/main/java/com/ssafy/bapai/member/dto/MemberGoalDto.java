package com.ssafy.bapai.member.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MemberGoalDto {
    private Long goalId;
    private Long userId;

    // 입력 정보
    private String activityLevel;
    private String dietGoal;

    // 분석 결과
    private double bmr;
    private double tdee;
    private double recCalories;
    private double recCarbs;
    private double recProtein;
    private double recFat;

    private LocalDateTime updatedAt;
}
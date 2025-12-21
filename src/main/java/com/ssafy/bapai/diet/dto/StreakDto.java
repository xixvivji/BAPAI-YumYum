package com.ssafy.bapai.diet.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StreakDto {
    private int currentStreak;  // 현재 연속 일수
    private int longestStreak;  // 최장 연속 일수
}
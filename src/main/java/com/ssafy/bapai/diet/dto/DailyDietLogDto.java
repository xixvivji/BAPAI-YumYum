package com.ssafy.bapai.diet.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyDietLogDto {
    private String date;

    // 통계 정보들 (그대로 유지)
    private int waterCupCount;
    private int waterGoal;
    private int totalMealCount;
    private int totalSnackCount;
    private double totalCalories;
    private double totalCarbs;
    private double totalProtein;
    private double totalFat;

    // ★ 변경됨: 펼쳐진 리스트로 교체
    private List<DietLogItemDto> dietList;
}
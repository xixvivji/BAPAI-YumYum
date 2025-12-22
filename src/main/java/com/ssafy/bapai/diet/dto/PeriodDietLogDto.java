package com.ssafy.bapai.diet.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeriodDietLogDto {
    // 1. 기간 정보
    private String startDate;
    private String endDate;

    // 6가지 최상위 합계 (기간 전체 총합)
    private int totalMealCount;
    private int totalSnackCount;
    private double totalCalories;
    private double totalCarbs;
    private double totalProtein;
    private double totalFat;

    // 3. 기존의 일별 데이터 리스트 (Map 형태 유지)
    private Map<String, DailyDietLogDto> dailyLogs;
}
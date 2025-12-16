package com.ssafy.bapai.ai.dto;

import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiReportResponse {
    private String type;            // DAILY, WEEKLY, MONTHLY
    private String dateRange;       // 예: "2024-05-01 ~ 2024-05-07"
    private String aiAnalysis;      // AI 분석

    // 일간용
    private List<DietDto> dailyMeals;

    // 주간/월간용
    private Double averageScore;      // 평균 점수
    private List<Integer> scoreTrend; // 점수 그래프 데이터
}
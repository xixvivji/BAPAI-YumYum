package com.ssafy.bapai.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GapReportDto {
    // 1. 내 현재 상태
    private double myAvgScore;
    private double myTotalKcal;

    // 2. 내 목표 (MemberGoal)
    private double goalKcal;

    // 3. 팀 상위 랭커 평균 (비교군)
    private double rankerAvgScore;
    private double rankerTotalKcal;

    // 4. AI 분석 결과
    private String aiAnalysis;
}
package com.ssafy.bapai.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "비교 분석 리포트 (나 vs 랭커 vs 목표)")
public class GapReportDto {

    // 1. 내 현재 상태 (선택한 기간 평균)
    @Schema(description = "나의 평균 점수")
    private double myAvgScore;
    private double myAvgCalories;
    private double myAvgCarbs;
    private double myAvgProtein;
    private double myAvgFat;

    // 2. 내 목표 (MemberGoal에서 가져옴 - 기준점)
    @Schema(description = "나의 목표 칼로리 (설정값)")
    private double goalCalories;
    private double goalCarbs;
    private double goalProtein;
    private double goalFat;

    // 3. 팀 상위 랭커 평균 (비교군)
    @Schema(description = "팀 상위 3명의 평균 점수")
    private double rankerAvgScore;
    private double rankerAvgCarbs;
    private double rankerAvgProtein;
    private double rankerAvgFat;

    // 4. AI 분석 결과
    @Schema(description = "AI가 분석한 조언 메시지")
    private String analysisMessage;
}
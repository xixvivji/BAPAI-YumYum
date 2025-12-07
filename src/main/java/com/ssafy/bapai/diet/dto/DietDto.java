package com.ssafy.bapai.diet.dto;

import java.util.List;
import lombok.Data;

@Data
public class DietDto {
    private Long dietId;        // PK
    private Long userId;        // 회원 ID
    private String eatDate;     // 식사 날짜 (YYYY-MM-DD)
    private String mealType;    // BREAKFAST, LUNCH, DINNER, SNACK
    private String dietImg;     // 사진 URL
    private String memo;        // 메모
    private Double totalKcal;   // 총 칼로리
    private Integer score;      // 건강 점수 (AI 분석 후 들어감)
    private String aiAnalysis;  // AI 분석 멘트

    // 식단에 포함된 음식 리스트 (등록/조회용)
    private List<DietDetailDto> foodList;
}
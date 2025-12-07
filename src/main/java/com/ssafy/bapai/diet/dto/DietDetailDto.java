package com.ssafy.bapai.diet.dto;

import lombok.Data;

@Data
public class DietDetailDto {
    private Long detailId;
    private Long dietId;
    private String foodCode;    // 식품 코드
    private String foodName;    // 식품명
    private int amount;         // 섭취량 (g)
    private Double kcal;        // 칼로리 (단일 품목)
}
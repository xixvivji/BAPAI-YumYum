package com.ssafy.bapai.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodDto {
    private String foodCode;      // PK
    private String dataSource;    // GENERAL, PROCESSED
    private String name;          // 식품명
    private String maker;         // 제조사
    private String category;      // 분류
    private Double servingSize;   // 1회 제공량
    private String unit;          // 단위

    // 영양소
    private Double kcal;
    private Double carbo;
    private Double protein;
    private Double fat;
    private Double sugar;
    private Double sodium;
    private Double cholesterol;
    private Double saturatedFat;
    private Double transFat;
}
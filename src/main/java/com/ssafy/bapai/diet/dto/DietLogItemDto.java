package com.ssafy.bapai.diet.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DietLogItemDto {
    private Long dietId;
    private String date;        // "2023-12-01"
    private String foodName;    // "닭가슴살 샐러드"
    private double kcal;
    private double carbs;
    private double protein;
    private double fat;
    private String mealType;    // "점심" (또는 LUNCH)
    private String time;        // "12:30" (없으면 null)
    private int servings;       // amount
}
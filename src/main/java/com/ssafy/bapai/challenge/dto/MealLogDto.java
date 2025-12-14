package com.ssafy.bapai.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "개별 식단 인증 기록 (AI 분석 결과)")
public class MealLogDto {
    private Long mealId;
    private Long userId;

    @Schema(description = "챌린지 인증용일 경우 ID값 (없으면 null)")
    private Long challengeId;

    private String imgUrl;
    private String content;

    // AI 분석 데이터 (서버 생성)
    private String menuName;
    private double calories;
    private double carbs;
    private double protein;
    private double fat;
    private int score;

    private LocalDateTime createdAt;
}
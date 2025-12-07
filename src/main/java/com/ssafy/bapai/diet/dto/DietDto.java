package com.ssafy.bapai.diet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "식단 정보 객체 (등록/조회)")
public class DietDto {

    @Schema(description = "식단 ID (PK)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long dietId;

    @Schema(description = "회원 ID", hidden = true) // 토큰에서 뽑으니까 숨김
    private Long userId;

    @Schema(description = "식사 날짜 (YYYY-MM-DD)", example = "2025-12-07")
    private String eatDate;

    @Schema(description = "식사 구분 (BREAKFAST, LUNCH, DINNER, SNACK)", example = "LUNCH")
    private String mealType;

    @Schema(description = "식단 사진 URL", example = "https://s3.aws.com/my-diet.jpg")
    private String dietImg;

    @Schema(description = "식사 메모", example = "운동 후 닭가슴살 샐러드")
    private String memo;

    @Schema(description = "총 칼로리 (서버 자동 계산)", accessMode = Schema.AccessMode.READ_ONLY)
    private Double totalKcal;

    @Schema(description = "건강 점수 (AI 분석)", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer score;

    @Schema(description = "AI 분석 코멘트", accessMode = Schema.AccessMode.READ_ONLY)
    private String aiAnalysis;

    @Schema(description = "포함된 음식 목록")
    private List<DietDetailDto> foodList;
}
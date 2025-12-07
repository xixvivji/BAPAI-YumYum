package com.ssafy.bapai.diet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "식단 상세 메뉴 (음식 정보)")
public class DietDetailDto {

    @Schema(description = "상세 ID (PK)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long detailId;

    @Schema(description = "식단 ID (FK)", hidden = true)
    private Long dietId;

    @Schema(description = "식품 코드 (식약처 DB 기준)", example = "D000006")
    private String foodCode;

    @Schema(description = "식품명", example = "닭가슴살")
    private String foodName;

    @Schema(description = "섭취량 (g)", example = "150")
    private int amount;

    @Schema(description = "섭취 칼로리 (해당 양 기준)", example = "165.0")
    private Double kcal;
}
package com.ssafy.bapai.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;

@Data
@Schema(description = "챌린지 정보")
public class ChallengeDto {
    private Long challengeId;

    @Schema(description = "모임 ID (FK)")
    private Long groupId;

    @Schema(description = "챌린지 제목")
    private String title;
    private String content;
    private LocalDate startDate;
    private LocalDate endDate;

    @Schema(description = "목표 타입 (COUNT:횟수, SCORE:점수)")
    private String goalType;

    @Schema(description = "목표 달성 횟수 (예: 5회 인증 시 성공)")
    private int targetCount;

    @Schema(description = "인정 최소 점수 (예: 60점 이상만 인정)")
    private int minScore;

    // 응답용 상태 (진행중/종료)
    private String status;
}
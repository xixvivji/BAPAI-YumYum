package com.ssafy.bapai.challenge.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ChallengeSelectRequest {
    private Long presetId;       // 선택한 프리셋 ID
    private LocalDate startDate; // 시작일
    private LocalDate endDate;   // 종료일
}
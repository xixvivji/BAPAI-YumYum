package com.ssafy.bapai.challenge.dto;

import lombok.Data;

@Data
public class ChallengePresetDto {
    private Long presetId;
    private String keyword;
    private String title;
    private String content;
    private String goalType;    // COUNT, SCORE
    private int targetCount;
}
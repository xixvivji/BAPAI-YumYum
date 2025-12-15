package com.ssafy.bapai.group.dto;

import lombok.Data;

@Data
public class GroupRankDto {
    private int rank;           // 등수
    private Long userId;
    private String nickname;
    private int totalScore;     // 총 점수
    private int reportCount;    // 인증 횟수
}
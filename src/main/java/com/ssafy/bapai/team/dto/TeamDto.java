package com.ssafy.bapai.team.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TeamDto {
    private Long teamId;
    private Long leaderId;
    private String name;
    private String description;
    private String teamImg;
    private String tags;
    private int maxMembers;
    private String type; // PUBLIC, PRIVATE
    private LocalDateTime createdAt;

    // 화면 표시용
    private String leaderName;
    private int memberCount;
    private boolean isJoined;
    private String myRole; // LEADER, MEMBER
}
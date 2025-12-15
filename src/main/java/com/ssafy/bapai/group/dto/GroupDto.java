package com.ssafy.bapai.group.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class GroupDto {
    // groups 테이블 컬럼
    private Long groupId;
    private Long ownerId;
    private String name;
    private String description;
    private String imgUrl;
    private int maxCount;
    private String type;        // PUBLIC, PRIVATE
    private LocalDateTime createdAt;

    // group_hashtags 관련 (화면에서 태그 목록을 주고받기 위함)
    private List<String> tags;

    // 화면 표시용 (JOIN 데이터)
    private String ownerName;   // 방장 닉네임
    private int memberCount;    // 현재 인원
    private boolean isJoined;   // 가입 여부
    private String myRole;      // LEADER, MEMBER
}
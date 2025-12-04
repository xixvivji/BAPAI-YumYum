package com.ssafy.bapai.member.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAuthDto {
    private Long authId;
    private String email;
    private String authCode;
    private boolean isVerified;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
}
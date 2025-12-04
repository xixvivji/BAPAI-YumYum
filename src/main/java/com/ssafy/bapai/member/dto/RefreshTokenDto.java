package com.ssafy.bapai.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "리프레시 토큰 DTO")
public class RefreshTokenDto {
    @Schema(description = "사용자 ID (PK)", example = "1")
    private String rtKey;
    @Schema(description = "리프레시 토큰 값", example = "eyJ...")
    private String rtValue;
    @Schema(description = "만료 시간", hidden = true)
    private String expiration;
}
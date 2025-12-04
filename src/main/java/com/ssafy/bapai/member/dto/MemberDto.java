package com.ssafy.bapai.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "회원 정보 객체 (요청/응답 공용)")
public class MemberDto {

    // ==========================
    // 1. 기본 계정 정보
    // ==========================

    @Schema(description = "회원 고유 번호 (PK)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long userId;

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
    @Schema(description = "로그인 아이디", example = "ssafy_king")
    private String loginId;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Schema(description = "이메일 (연락처)", example = "test01@ssafy.com")
    private String email;

    // ★ 핵심: 응답(Response)으로 나갈 때는 이 필드를 숨김 (비밀번호 유출 방지)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "비밀번호 (소셜 로그인은 생략 가능)", example = "1234")
    private String password;

    @NotBlank(message = "이름(실명)은 필수입니다.")
    @Size(max = 10, message = "이름은 10자 이내여야 합니다.")
    @Schema(description = "사용자 실명", example = "김싸피")
    private String name;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2~10자 사이여야 합니다.")
    @Schema(description = "사용자 닉네임 (중복 불가)", example = "냠냠박사")
    private String nickname;

//    @Schema(description = "프로필 이미지 URL", example = "/images/default_profile.png")
//    private String profileImg; // [추가됨]

    // ==========================
    // 2. 권한 및 소셜 정보 (서버 관리)
    // ==========================

    @Schema(description = "권한 (ROLE_GUEST: 미인증, ROLE_USER: 정회원)", example = "ROLE_USER", accessMode = Schema.AccessMode.READ_ONLY)
    private String role;

    @Schema(description = "가입 경로 (LOCAL, KAKAO, GOOGLE, NAVER)", example = "LOCAL", accessMode = Schema.AccessMode.READ_ONLY)
    private String provider;

    @Schema(description = "소셜 식별값", hidden = true)
    private String providerId;

    // ==========================
    // 3. 신체 및 건강 정보 (2단계 입력)
    // ==========================

    @Schema(description = "출생년도", example = "1998")
    private Integer birthYear;

    @Schema(description = "성별 (M/F)", example = "M")
    private String gender;

    @Schema(description = "키 (cm)", example = "175.5")
    private Double height;

    @Schema(description = "몸무게 (kg)", example = "72.0")
    private Double weight;

    @Schema(description = "활동량 (LOW, NORMAL, HIGH)", example = "NORMAL")
    private String activityLevel;

    // ==========================
    // 4. 분석 데이터
    // ==========================

    @Schema(description = "식단 목표 (LOSS, MAINTAIN, GAIN)", example = "LOSS")
    private String dietGoal;

    @Schema(description = "분석된 권장 칼로리 (서버 자동계산)", hidden = true)
    private Double customTdee;

    @Schema(description = "기저질환 태그 (콤마 구분)", example = "DIABETES,HYPERTENSION")
    private String diseaseCodes;

    @Schema(description = "알레르기 태그 (콤마 구분)", example = "PEACH,SHRIMP")
    private String allergyCodes;

    // ==========================
    // 5. 시스템 로그 (읽기 전용)
    // ==========================

    @Schema(description = "계정 상태 (ACTIVE, WITHDRAWN)", hidden = true)
    private String status;

    @Schema(description = "가입일", hidden = true)
    private String joinedAt;

    @Schema(description = "마지막 로그인 시간", hidden = true)
    private String lastLoginAt;
}
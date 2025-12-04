package com.ssafy.bapai.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data              // Getter, Setter, ToString, EqualsAndHashCode 자동 생성
@NoArgsConstructor // 기본 생성자 생성
@AllArgsConstructor // 모든 필드를 받는 생성자 생성
@Builder           // 빌더 패턴 사용 가능 (MemberDto.builder().build())
public class MemberDto {

    // 1. 식별자 & 계정
    private Long userId;          // PK
    
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 20)
    @Schema(description = "로그인 아이디", example = "ssafy_king")
    private String loginId;


    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    // 비밀번호는 소셜 로그인 시 null일 수 있으므로, 회원가입/수정 시 별도로 체크하거나
    // 여기서는 @Pattern 등으로 최소 조건만 겁니다.
    private String password;

    @NotBlank(message = "이름(실명)은 필수입니다.")
    @Size(max = 10, message = "이름은 10자 이내여야 합니다.")
    private String name;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2~10자 사이여야 합니다.")
    // @Pattern(regexp = "^[가-힣a-zA-Z0-9]*$", message = "닉네임은 특수문자를 포함할 수 없습니다.") // 필요하면 주석 해제
    private String nickname;


    private String role;          // ROLE_GUEST, ROLE_USER
    private String provider;      // KAKAO, LOCAL
    private String providerId;    // 소셜 식별값

    // 2. 신체 정보
    private Integer birthYear;    // 출생년도
    private String gender;        // M, F
    private Double height;        // 키
    private Double weight;        // 몸무게
    private String activityLevel; // 활동량 (LOW, NORMAL, HIGH)

    // 3. 목표 및 분석 데이터
    private String dietGoal;      // LOSS(감량), MAINTAIN(유지), GAIN(증량)
    private Double customTdee;    // 분석된 진짜 권장 칼로리

    // 4. 건강 태그 (DB에는 콤마로 구분된 문자열로 저장)
    private String diseaseCodes;
    private String allergyCodes;

    // 5. 상태 및 로그
    private String status;        // ACTIVE, DORMANT, WITHDRAWN
    private String joinedAt;      // 가입일
    private String lastLoginAt;   // 마지막 로그인
}
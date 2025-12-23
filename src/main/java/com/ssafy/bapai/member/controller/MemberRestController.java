package com.ssafy.bapai.member.controller;

import com.ssafy.bapai.common.redis.RefreshToken;
import com.ssafy.bapai.common.redis.RefreshTokenRepository;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.group.service.GroupService;
import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.MemberGoalDto;
import com.ssafy.bapai.member.service.EmailService;
import com.ssafy.bapai.member.service.HealthService;
import com.ssafy.bapai.member.service.MemberService;
import com.ssafy.bapai.member.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "1. 회원(Member) API", description = "회원가입, 로그인, 마이페이지 등 회원 관련 기능")
public class MemberRestController {

    private final MemberService memberService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final OAuthService oAuthService;
    private final S3Service s3Service;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HealthService healthService;
    private final GroupService groupService;
    // =================================================================================
    // 1. 인증 & 가입 (Auth)
    // =================================================================================

    @Operation(summary = "일반 회원가입 (1단계)", description = "아이디, 이메일, 비밀번호, 이름, 닉네임을 입력받아 계정을 생성합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"username\": \"ssafy_king\", \"email\": \"test@ssafy.com\", \"password\": \"1234\", \"name\": \"김싸피\", \"nickname\": \"냠냠박사\"}")
            )
    )
    @PostMapping("/auth/signup")
    public ResponseEntity<?> signup(@RequestBody MemberDto member) {
        try {
            memberService.signup(member);
            return new ResponseEntity<>(Map.of("message", "회원가입 성공"), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("message", "회원가입 실패: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "일반 로그인", description = "아이디와 비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"username\": \"ssafy_king\", \"password\": \"1234\"}")
            )
    )
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody MemberDto member) {
        MemberDto loginUser = memberService.login(member.getUsername(), member.getPassword());

        if (loginUser != null) {
            String accessToken =
                    jwtUtil.createAccessToken(loginUser.getUserId(), loginUser.getRole());
            String refreshToken =
                    jwtUtil.createRefreshToken(loginUser.getUserId(), loginUser.getRole());

            RefreshToken redisToken = RefreshToken.builder()
                    .userId(String.valueOf(loginUser.getUserId()))
                    .token(refreshToken)
                    .expiration(1209600L) // 14일
                    .build();
            refreshTokenRepository.save(redisToken);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", loginUser.getUserId());
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            result.put("role", loginUser.getRole());
            result.put("name", loginUser.getName());
            result.put("isHealthInfoNeeded", "ROLE_GUEST".equals(loginUser.getRole()));
            result.put("isTempPassword", loginUser.isTempPassword());
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "아이디 또는 비밀번호가 일치하지 않습니다."));
        }
    }

    @Operation(summary = "카카오 로그인")
    @PostMapping("/auth/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody Map<String, String> req) {
        return processSocialLogin("KAKAO", req.get("code"));
    }

    @Operation(summary = "구글 로그인")
    @PostMapping("/auth/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> req) {
        return processSocialLogin("GOOGLE", req.get("code"));
    }

    @Operation(summary = "네이버 로그인")
    @PostMapping("/auth/naver")
    public ResponseEntity<?> naverLogin(@RequestBody Map<String, String> req) {
        return processSocialLogin("NAVER", req.get("code"));
    }

    private ResponseEntity<?> processSocialLogin(String provider, String code) {
        try {
            String accessToken = oAuthService.getAccessToken(provider, code);
            Map<String, Object> userInfo = oAuthService.getUserInfo(provider, accessToken);

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String providerId = (String) userInfo.get("providerId");

            MemberDto member = memberService.socialLogin(email, name, provider, providerId);

            String token = jwtUtil.createAccessToken(member.getUserId(), member.getRole());
            String refreshToken = jwtUtil.createRefreshToken(member.getUserId(), member.getRole());

            RefreshToken redisToken = RefreshToken.builder()
                    .userId(String.valueOf(member.getUserId()))
                    .token(refreshToken)
                    .expiration(1209600L) // 14일 (초 단위)
                    .build();
            refreshTokenRepository.save(redisToken);
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", token);
            result.put("refreshToken", refreshToken);
            result.put("role", member.getRole());
            result.put("name", member.getName());
            result.put("isHealthInfoNeeded", "ROLE_GUEST".equals(member.getRole()));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "소셜 로그인 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.ok(Map.of("message", "이미 로그아웃 상태입니다."));
        }
        try {
            String accessToken = token.substring(7);
            Long userId = jwtUtil.getUserId(accessToken);

            // 1. Redis에서 Refresh Token 삭제
            refreshTokenRepository.deleteById(String.valueOf(userId));

            // 2. Access Token 블랙리스트 등록 (남은 시간만큼)
            Long expiration = jwtUtil.getExpiration(accessToken);
            memberService.registerBlacklist(userId, accessToken, expiration); // Service에 구현 필요

            return ResponseEntity.ok(Map.of("message", "로그아웃 성공"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "로그아웃 처리됨 (이미 만료됨)"));
        }
    }

    // =================================================================================
    // 2. 계정 찾기 & 검증 (Validation)
    // =================================================================================

    @Operation(summary = "이메일 중복 체크")
    @GetMapping("/auth/check-email")
    public ResponseEntity<?> checkEmail(
            @Parameter(description = "확인할 이메일") @RequestParam String email) {
        if (memberService.isEmailDuplicate(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 사용 중인 이메일입니다."));
        }
        return ResponseEntity.ok(Map.of("message", "사용 가능한 이메일입니다."));
    }

    @Operation(summary = "닉네임 중복 체크")
    @GetMapping("/auth/check-nickname")
    public ResponseEntity<?> checkNickname(
            @Parameter(description = "확인할 닉네임") @RequestParam String nickname) {
        if (memberService.isNicknameDuplicate(nickname)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 사용 중인 닉네임입니다."));
        }
        return ResponseEntity.ok(Map.of("message", "사용 가능한 닉네임입니다."));
    }

    @Operation(summary = "아이디 중복 체크")
    @GetMapping("/auth/check-username")
    public ResponseEntity<?> checkUsername(
            @Parameter(description = "확인할 아이디") @RequestParam String username) {
        if (memberService.isUsernameDuplicate(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 사용 중인 아이디입니다."));
        }
        return ResponseEntity.ok(Map.of("message", "사용 가능한 아이디입니다."));
    }

    @Operation(summary = "이메일 인증번호 발송")
    @PostMapping("/auth/email/send")
    public ResponseEntity<?> sendEmail(@RequestBody Map<String, String> req) {
        try {
            emailService.sendVerificationCode(req.get("email"));
            return ResponseEntity.ok(Map.of("message", "인증번호 발송 완료"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "발송 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "이메일 인증번호 확인")
    @PostMapping("/auth/email/verify")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> req) {
        if (emailService.verifyCode(req.get("email"), req.get("code"))) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false));
    }

    @Operation(summary = "아이디 찾기")
    @PostMapping("/auth/find-username")
    public ResponseEntity<?> findId(@RequestBody Map<String, String> req) {
        try {
            String username = memberService.findUsername(req.get("email"), req.get("code"));
            return ResponseEntity.ok(Map.of("username", username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // =================================================================================
    // 3. 비밀번호 관리
    // =================================================================================

    @Operation(summary = "비밀번호 찾기 1단계 (본인확인)")
    @PostMapping("/auth/verify-reset")
    public ResponseEntity<?> verifyForReset(@RequestBody Map<String, String> req) {
        boolean isValid = memberService.verifyUserForReset(req.get("username"), req.get("email"),
                req.get("code"));
        if (isValid) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "정보 불일치"));
    }

    @Operation(summary = "비밀번호 찾기 2단계 (임시 비밀번호 발송)")
    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> req) {
        boolean result =
                memberService.resetPassword(req.get("username"), req.get("email"), req.get("code"));
        if (result) {
            return ResponseEntity.ok(Map.of("message", "임시 비밀번호가 이메일로 전송되었습니다."));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "인증 실패 또는 회원 정보 없음"));
        }
    }

    @Operation(summary = "새 비밀번호 중복 확인")
    @PostMapping("/members/check-new-password")
    public ResponseEntity<?> checkNewPassword(@RequestHeader("Authorization") String token,
                                              @RequestBody Map<String, String> req) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        if (memberService.checkPassword(userId, req.get("newPassword"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "현재 사용 중인 비밀번호입니다."));
        }
        return ResponseEntity.ok(Map.of("message", "사용 가능한 비밀번호입니다."));
    }

    @Operation(summary = "비밀번호 확인 (마이페이지 접근 전)")
    @PostMapping("/members/check-password")
    public ResponseEntity<?> checkPassword(@RequestHeader("Authorization") String token,
                                           @RequestBody Map<String, String> req) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        boolean match = memberService.checkPassword(userId, req.get("password"));
        return ResponseEntity.ok(Map.of("match", match));
    }

    @Operation(summary = "비밀번호 변경")
    @PatchMapping("/members/password")
    public ResponseEntity<?> updatePassword(@RequestHeader("Authorization") String token,
                                            @RequestBody Map<String, String> req) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        String newPassword = req.get("newPassword");

        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "새 비밀번호를 입력해주세요."));
        }

        memberService.updatePassword(userId, newPassword);
        return ResponseEntity.ok(Map.of("message", "비밀번호 변경 완료"));
    }

    // =================================================================================
    // 4. 회원 정보 관리 (MyPage)
    // =================================================================================

    @Operation(summary = "내 정보 조회")
    @GetMapping("/members/me")
    public ResponseEntity<?> getMyInfo(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        MemberDto member = memberService.getMember(userId);
        if (member != null) {
            member.setPassword(null);
        }
        return ResponseEntity.ok(member);
    }

    @Operation(summary = "회원 정보 수정 (건강 정보 포함)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MemberDto.class),
                    examples = @ExampleObject(value = "{\n" +
                            "  \"height\": 175.5,\n" +
                            "  \"weight\": 72,\n" +
                            "  \"activityLevel\": \"NORMAL\",\n" +
                            "  \"dietGoal\": \"LOSS\",\n" +
                            "  \"diseaseIds\": [ 1, 5 ],\n" +
                            "  \"allergyIds\": [ 2 ],\n" +
                            "  \"birthYear\": 1998,\n" +
                            "  \"gender\": \"M\"\n" +
                            "}")
            )
    )
    @PatchMapping("/members/me")
    public ResponseEntity<?> updateMyInfo(@RequestHeader("Authorization") String token,
                                          @RequestBody MemberDto member) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        member.setUserId(userId);

        MemberDto currentMember = memberService.getMember(userId);

        // 게스트가 필수 정보 입력 시 정회원으로 등업
        if ("ROLE_GUEST".equals(currentMember.getRole()) &&
                member.getHeight() != null && member.getWeight() != null) {
            member.setRole("ROLE_USER");
        }

        memberService.updateMember(member);

        String newToken = jwtUtil.createAccessToken(userId,
                member.getRole() != null ? member.getRole() : currentMember.getRole());

        return ResponseEntity.ok(Map.of("message", "수정 완료", "accessToken", newToken));
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping("/members/me")
    public ResponseEntity<?> withdraw(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        memberService.withdraw(userId);
        return ResponseEntity.ok(Map.of("message", "탈퇴 완료"));
    }

    @Operation(summary = "질병/알레르기 옵션 목록 조회")
    @GetMapping("/members/options")
    public ResponseEntity<?> getHealthOptions() {
        return ResponseEntity.ok(memberService.getHealthOptions());
    }

    @Operation(summary = "내 건강정보 기반 목표 분석", description = "JWT로 내 정보를 조회한 뒤 BMR/TDEE 및 탄단지 권장량을 계산합니다.")
    @GetMapping("/health/analyze/me")
    public ResponseEntity<?> analyzeMyBody(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token.substring(7));

        MemberDto member = memberService.getMember(userId);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "회원 정보를 찾을 수 없습니다."));
        }

        if (member.getBirthYear() == null ||
                member.getHeight() == null ||
                member.getWeight() == null ||
                member.getGender() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "건강정보가 부족합니다. (birthYear/height/weight/gender 필수)",
                    "isHealthInfoNeeded", true
            ));
        }

        MemberGoalDto result = healthService.calculateHealthMetrics(member);
        return ResponseEntity.ok(result);
    }


    @Operation(summary = "토큰 재발급 (RTR 적용)")
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> req) {
        String oldRefreshToken = req.get("refreshToken");

        // 1. 유효성 검증
        if (oldRefreshToken == null || !jwtUtil.validateToken(oldRefreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "리프레시 토큰이 만료되었거나 유효하지 않습니다."));
        }

        // 2. 사용자 식별 (Bearer prefix 처리 포함됨)
        Long userId = jwtUtil.getUserId("Bearer " + oldRefreshToken);

        // 3. Redis에 저장된 토큰과 대조 (보안 핵심: RTR)
        RefreshToken storedToken =
                refreshTokenRepository.findById(String.valueOf(userId)).orElse(null);

        if (storedToken == null || !storedToken.getToken().equals(oldRefreshToken)) {
            // [중요] 토큰 탈취 의심 상황: Redis에 저장된 토큰과 다른 토큰이 들어옴
            // 이 경우 해당 유저의 모든 리프레시 토큰을 지워 강제 로그아웃 시키는 것이 안전합니다.
            refreshTokenRepository.deleteById(String.valueOf(userId));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "토큰이 일치하지 않습니다. 다시 로그인해주세요."));
        }

        // 4. 새 토큰 세트 생성 (yml 설정값 활용)
        MemberDto member = memberService.getMember(userId);
        String newAccessToken = jwtUtil.createAccessToken(userId, member.getRole());
        String newRefreshToken = jwtUtil.createRefreshToken(userId, member.getRole());

        // 5. Redis 갱신 (기존 토큰 덮어쓰기)
        // RefreshToken 엔티티의 @RedisHash 설정에 따라 유효기간이 관리됩니다.
        RefreshToken nextToken = RefreshToken.builder()
                .userId(String.valueOf(userId))
                .token(newRefreshToken)
                .expiration(1209600L) // 14일 (초 단위)
                .build();
        refreshTokenRepository.save(nextToken);

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        ));
    }


    // 1. 그룹 멤버 목록 조회
    @GetMapping("/groups/{groupId}/members")
    @Operation(summary = "그룹 멤버 목록 조회")
    public ResponseEntity<List<MemberDto>> getGroupMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupMembers(groupId));
    }

    // 2. 초대할 사용자 검색 (닉네임 기준)
    @GetMapping("/groups/{groupId}/search-users")
    @Operation(summary = "초대할 사용자 검색", description = "그룹에 가입되지 않은 사용자 중 닉네임으로 검색합니다.")
    public ResponseEntity<List<MemberDto>> searchUsers(
            @PathVariable Long groupId,
            @RequestParam String nickname) {
        return ResponseEntity.ok(groupService.searchUsers(nickname, groupId));
    }
}
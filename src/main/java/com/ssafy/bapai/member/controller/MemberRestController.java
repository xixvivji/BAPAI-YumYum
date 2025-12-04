package com.ssafy.bapai.member.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.member.dao.RefreshTokenDao;
import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.RefreshTokenDto;
import com.ssafy.bapai.member.service.EmailService;
import com.ssafy.bapai.member.service.MemberService;
import com.ssafy.bapai.member.service.OAuthService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
//@CrossOrigin("*") // Vue.js와 통신을 위해 CORS 허용
public class MemberRestController {

    private final MemberService memberService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final OAuthService oAuthService;
    private final RefreshTokenDao refreshTokenDao;
    // ==========================
    // [1. 인증 & 가입]
    // ==========================

    // 1-1. 일반 회원가입 (1단계: 계정 생성)
    @PostMapping("/auth/signup")
    public ResponseEntity<?> signup(@RequestBody MemberDto member) {
        try {
            memberService.signup(member);
            return new ResponseEntity<>("회원가입 성공", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("회원가입 실패: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 1-2. 일반 로그인
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody MemberDto member) {
        MemberDto loginUser = memberService.login(member.getEmail(), member.getPassword());

        if (loginUser != null) {
            // 1. Access Token (1시간)
            String accessToken =
                    jwtUtil.createToken(loginUser.getUserId(), loginUser.getRole(), 1000 * 60 * 60);

            // 2. Refresh Token (2주)
            long refreshTokenExpireTime = 1000L * 60 * 60 * 24 * 14; // 2주 (밀리초)
            String refreshToken = jwtUtil.createToken(loginUser.getUserId(), loginUser.getRole(),
                    refreshTokenExpireTime);


            String expirationStr = java.time.LocalDateTime.now().plusDays(14).toString();


            // 3. DB 저장
            RefreshTokenDto rtDto = RefreshTokenDto.builder()
                    .rtKey(String.valueOf(loginUser.getUserId()))
                    .rtValue(refreshToken)
                    .expiration(expirationStr) // ★ [수정] null 대신 실제 만료 시간 입력!
                    .build();
            refreshTokenDao.save(rtDto);

            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            result.put("role", loginUser.getRole());
            result.put("name", loginUser.getName());
            result.put("isHealthInfoNeeded", "ROLE_GUEST".equals(loginUser.getRole()));

            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 실패");
        }
    }

    // 1-3. 카카오 로그인
    @PostMapping("/auth/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody Map<String, String> req) {
        return processSocialLogin("KAKAO", req.get("code"));
    }

    // 1-4. 구글 로그인
    @PostMapping("/auth/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> req) {
        return processSocialLogin("GOOGLE", req.get("code"));
    }

    // 1-5. 네이버 로그인 (추후 구현)
    @PostMapping("/auth/naver")
    public ResponseEntity<?> naverLogin(@RequestBody Map<String, String> req) {
        return processSocialLogin("NAVER", req.get("code"));
    }

    // [공통 로직 메서드] - 중복 코드를 제거하기 위해 별도로 뺌
    private ResponseEntity<?> processSocialLogin(String provider, String code) {
        try {
            // 1. 액세스 토큰 요청
            String accessToken = oAuthService.getAccessToken(provider, code);

            // 2. 사용자 정보 요청
            Map<String, Object> userInfo = oAuthService.getUserInfo(provider, accessToken);
            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String providerId = (String) userInfo.get("providerId");

            // 3. 로그인/가입 로직 진행
            MemberDto member = memberService.socialLogin(email, name, provider, providerId);

            // 4. JWT 발급 (Access + Refresh)
            String token =
                    jwtUtil.createToken(member.getUserId(), member.getRole(), 1000 * 60 * 60);
            String refreshToken = jwtUtil.createToken(member.getUserId(), member.getRole(),
                    1000L * 60 * 60 * 24 * 14);

            // 5. Refresh Token 저장
            RefreshTokenDto rtDto = RefreshTokenDto.builder()
                    .rtKey(String.valueOf(member.getUserId()))
                    .rtValue(refreshToken)
                    .expiration(java.time.LocalDateTime.now().plusWeeks(2).toString())
                    .build();
            refreshTokenDao.save(rtDto);

            // 6. 응답 생성
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
                    .body(provider + " 로그인 실패: " + e.getMessage());
        }
    }

    // 1-4. 로그아웃
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);
        refreshTokenDao.delete(String.valueOf(userId)); // DB에서 삭제
        return ResponseEntity.ok(Map.of("success", true, "message", "로그아웃 되었습니다."));
    }

    // 1-5.1. 이메일 중복 체크
    @GetMapping("/auth/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        if (memberService.isEmailDuplicate(email)) {
            // 중복이면 409 에러를 던짐
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 사용 중인 이메일입니다.");
        }
        // 사용 가능하면 200 OK
        return ResponseEntity.ok("사용 가능한 이메일입니다.");
    }

    // 1-5.2. 닉네임 중복 체크
    @GetMapping("/auth/check-nickname")
    public ResponseEntity<?> checkNickname(@RequestParam String nickname) {
        if (memberService.isNicknameDuplicate(nickname)) {
            // 중복이면 409 에러를 던짐
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 사용 중인 닉네임입니다.");
        }
        // 사용 가능하면 200 OK
        return ResponseEntity.ok("사용 가능한 닉네임입니다.");
    }

    // 1-5.3. 아이디 중복 체크 (추가)
    @GetMapping("/auth/check-id")
    public ResponseEntity<?> checkLoginId(@RequestParam String loginId) {
        if (memberService.isLoginIdDuplicate(loginId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 사용 중인 아이디입니다.");
        }
        return ResponseEntity.ok("사용 가능한 아이디입니다.");
    }

    // 1-6. 이메일 인증번호 전송
    @PostMapping("/auth/email/send")
    public ResponseEntity<?> sendEmail(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        try {
            emailService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of("message", "인증번호가 발송되었습니다. (유효시간 5분)"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("메일 발송 실패: " + e.getMessage());
        }
    }

    // 1-7. 이메일 인증번호 확인
    @PostMapping("/auth/email/verify")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        String code = req.get("code");

        if (emailService.verifyCode(email, code)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "인증 성공"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "인증 실패"));
        }
    }

    // 1-8. 비밀번호 찾기 (재설정)
    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        String code = req.get("code");
        String newPassword = req.get("newPassword");

        if (memberService.resetPassword(email, code, newPassword)) {
            return ResponseEntity.ok("비밀번호가 변경되었습니다.");
        } else {
            return ResponseEntity.badRequest().body("인증 실패 또는 회원 정보 없음");
        }
    }

    // 1-9 리프레쉬 토큰 재발급
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> req) {
        String refreshToken = req.get("refreshToken");

        // 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("리프레시 토큰 만료. 다시 로그인하세요.");
        }

        Long userId = jwtUtil.getUserId("Bearer " + refreshToken);
        String dbToken = refreshTokenDao.findToken(String.valueOf(userId));

        if (dbToken == null || !dbToken.equals(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        // 새 토큰 발급
        String newAccessToken = jwtUtil.createToken(userId, "ROLE_USER", 1000 * 60 * 60);

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    // ==========================
    // [2. 회원 정보 & 건강] (여기부터는 토큰 필수!)
    // ==========================

    // 2-1. 건강 정보 입력 (2단계: Guest -> User 등업)
//    @PutMapping("/members/health")
//    public ResponseEntity<?> updateHealthInfo(@RequestHeader("Authorization") String token,
//                                              @RequestBody MemberDto member) {
//        Long userId = jwtUtil.getUserId(token);
//        member.setUserId(userId);
//        member.setRole("ROLE_USER");
//
//        memberService.updateMember(member);
//
//        String newToken = jwtUtil.createToken(userId, "ROLE_USER");
//        return ResponseEntity.ok(Map.of("message", "완료", "accessToken", newToken));
//    }

    // 2-2. 내 정보 조회
    @GetMapping("/members/me")
    public ResponseEntity<?> getMyInfo(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);
        MemberDto member = memberService.getMember(userId);

        if (member != null) {
            member.setPassword(null);
            return ResponseEntity.ok(member);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("회원 정보를 찾을 수 없습니다.");
    }

    // 2-3. 정보 수정 (마이페이지) / 회원건강정보 입력

    @PatchMapping("/members/me")
    public ResponseEntity<?> updateMyInfo(@RequestHeader("Authorization") String token,
                                          @RequestBody MemberDto member) {
        Long userId = jwtUtil.getUserId(token);
        member.setUserId(userId);

        // 1. 현재 유저 정보 조회
        MemberDto currentMember = memberService.getMember(userId);

        // 2. 등업 로직 (GUEST -> USER)
        // 만약 키/몸무게 등 필수 정보가 들어왔고, 현재 권한이 GUEST라면 등업시켜줌
        if ("ROLE_GUEST".equals(currentMember.getRole()) &&
                member.getHeight() != null && member.getWeight() != null) {
            member.setRole("ROLE_USER");
        }

        // 3. 정보 수정 (Dynamic Query로 들어온 값만 수정됨)
        memberService.updateMember(member);

        // 4. 토큰 재발급 (권한이 바뀌었을 수 있으므로)
        String newToken = jwtUtil.createToken(userId,
                member.getRole() != null ? member.getRole() : currentMember.getRole());

        return ResponseEntity.ok(Map.of("message", "수정 완료", "accessToken", newToken));
    }


    // 2-4. 비밀번호 확인 (수정 전 본인확인용)
    @PostMapping("/members/check-password")
    public ResponseEntity<?> checkPassword(@RequestHeader("Authorization") String token,
                                           @RequestBody Map<String, String> req) {
        Long userId = jwtUtil.getUserId(token);
        String password = req.get("password");
        boolean match = memberService.checkPassword(userId, password);
        return ResponseEntity.ok(Map.of("match", match));
    }

    // 2-5. 비밀번호 변경
    @PatchMapping("/members/password")
    public ResponseEntity<?> updatePassword(@RequestHeader("Authorization") String token,
                                            @RequestBody Map<String, String> req) {
        Long userId = jwtUtil.getUserId(token);
        String newPassword = req.get("newPassword");
        memberService.updatePassword(userId, newPassword);
        return ResponseEntity.ok("비밀번호 변경 완료");
    }


    // 2-6. 회원 탈퇴
    @DeleteMapping("/members/me")
    public ResponseEntity<?> withdraw(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);
        memberService.withdraw(userId);
        return ResponseEntity.ok("탈퇴 완료");
    }
}
package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dto.MemberDto;

public interface MemberService {

    // 1. 회원가입 (1단계: 계정 생성)
    void signup(MemberDto member);

    // 2. 로그인
    MemberDto login(String email, String password);

    // 3. 회원 정보 조회
    MemberDto getMember(Long userId);

    boolean isLoginIdDuplicate(String loginId);

    // 4. 회원 정보 수정 (2단계: 건강 정보 입력 포함)
    void updateMember(MemberDto member);

    // 5. 중복 체크 (이메일)
    boolean isEmailDuplicate(String email);

    // 6. 중복 체크 (닉네임) - 닉네임이니까 중복 허용 안 함
    boolean isNicknameDuplicate(String nickname);

    // 7. 비밀번호 확인 (수정 전 검증용)
    boolean checkPassword(Long userId, String rawPassword);

    // 8. 비밀번호 변경
    void updatePassword(Long userId, String newPassword);

    // 9. 회원 탈퇴
    void withdraw(Long userId);

    //10. 비밀번호 초기화
    boolean resetPassword(String email, String code, String newPassword);

    // 소셜 로그인 (가입 안 되어 있으면 자동 가입)
    MemberDto socialLogin(String email, String name, String provider, String providerId);

    // [추가] 아이디 찾기 (이메일 인증 후 아이디 반환)
    String findLoginId(String email, String code);

    // [추가] 비밀번호 찾기 전, 본인 확인 (아이디+이메일+인증번호)
    boolean verifyUserForReset(String loginId, String email, String code);
}
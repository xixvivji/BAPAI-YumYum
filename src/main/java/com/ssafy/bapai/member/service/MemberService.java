package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dto.MemberDto;
import org.springframework.transaction.annotation.Transactional;

public interface MemberService {
    void signup(MemberDto member);

    // [수정] loginId -> username
    MemberDto login(String username, String password);

    MemberDto getMember(Long userId);

    void updateMember(MemberDto member);

    boolean isEmailDuplicate(String email);

    boolean isNicknameDuplicate(String nickname);

    // [수정] checkLoginId -> checkUsername
    boolean isUsernameDuplicate(String username);

    boolean checkPassword(Long userId, String rawPassword);

    void updatePassword(Long userId, String newPassword);

    void withdraw(Long userId);

    // [수정] findLoginId -> findUsername
    String findUsername(String email, String code);

    // [수정] verifyUserForReset 파라미터 변경
    boolean verifyUserForReset(String username, String email, String code);

    // 12. 비밀번호 초기화 (재설정)
    // [수정된 로직] 비밀번호 찾기 -> 임시 비밀번호 발급
    @Transactional
    boolean resetPassword(String username, String email, String code);

    MemberDto socialLogin(String email, String name, String provider, String providerId);
}
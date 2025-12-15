package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.OptionDto;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public interface MemberService {

    // 회원가입
    void signup(MemberDto member);

    // 로그인
    MemberDto login(String username, String password);

    // 회원 조회
    MemberDto getMember(Long userId);

    // 정보 수정
    void updateMember(MemberDto member);

    // 중복 체크들
    boolean isEmailDuplicate(String email);

    boolean isNicknameDuplicate(String nickname);

    boolean isUsernameDuplicate(String username);

    // 비밀번호 관련
    boolean checkPassword(Long userId, String rawPassword);

    void updatePassword(Long userId, String newPassword);

    // 탈퇴
    void withdraw(Long userId);

    // 아이디 찾기
    String findUsername(String email, String code);

    // 비밀번호 재설정 관련
    boolean verifyUserForReset(String username, String email, String code);

    @Transactional
    boolean resetPassword(String username, String email, String code);

    // 로그아웃
    void logout(Long userId);

    // 소셜 로그인
    MemberDto socialLogin(String email, String name, String provider, String providerId);

    Map<String, List<OptionDto>> getHealthOptions();
}
package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dao.MemberDao;
import com.ssafy.bapai.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberDao memberDao;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    // 1. 회원가입 (일반)
    @Override
    @Transactional
    public void signup(MemberDto member) {
        // 비밀번호 암호화 후 저장
        String encodedPwd = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPwd);

        memberDao.insertMember(member);
    }

    // 2. 로그인 (비밀번호 검증)
    @Override
    public MemberDto login(String loginId, String password) {
        // [수정] 이메일 대신 loginId로 찾기
        MemberDto member = memberDao.selectMemberByLoginId(loginId);

        if (member == null || "WITHDRAWN".equals(member.getStatus())) {
            return null;
        }
        if (!passwordEncoder.matches(password, member.getPassword())) {
            return null;
        }

        memberDao.updateLastLogin(member.getUserId());
        return member;
    }

    // 3. 회원 정보 조회
    @Override
    public MemberDto getMember(Long userId) {
        return memberDao.selectMemberById(userId);
    }

    // 4. 회원 정보 수정
    @Override
    @Transactional
    public void updateMember(MemberDto member) {
        memberDao.updateMember(member);
    }

    // 5. 이메일 중복 체크
    @Override
    public boolean isEmailDuplicate(String email) {
        return memberDao.checkEmail(email) > 0;
    }

    // 6. 닉네임 중복 체크
    @Override
    public boolean isNicknameDuplicate(String nickname) {

        return memberDao.checkNickname(nickname) > 0;
    }

    // 7. 비밀번호 확인 (수정 전 본인확인)
    @Override
    public boolean checkPassword(Long userId, String rawPassword) {
        MemberDto member = memberDao.selectMemberById(userId);
        if (member == null) {
            return false;
        }
        // 암호화된 비밀번호와 비교
        return passwordEncoder.matches(rawPassword, member.getPassword());
    }

    // 8. 비밀번호 변경
    @Override
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        // 새 비밀번호 암호화
        String encodedPwd = passwordEncoder.encode(newPassword);
        memberDao.updatePassword(userId, encodedPwd);
    }

    // 9. 회원 탈퇴
    @Override
    @Transactional
    public void withdraw(Long userId) {
        memberDao.deleteMember(userId);
    }

    // 10. 비밀번호 초기화 (이메일 인증 필수)
    @Override
    @Transactional
    public boolean resetPassword(String email, String code, String newPassword) {
        // (1) 인증 번호 검증
        if (!emailService.verifyCode(email, code)) {
            return false;
        }

        // (2) 회원 조회
        MemberDto member = memberDao.selectMemberByEmail(email);
        if (member == null) {
            return false;
        }

        // (3) 소셜 로그인 유저는 비밀번호 변경 불가 (LOCAL만 가능)
        if (!"LOCAL".equals(member.getProvider())) {
            return false;
        }

        // (4) 비밀번호 암호화 후 업데이트
        String encodedPwd = passwordEncoder.encode(newPassword);
        memberDao.updatePassword(member.getUserId(), encodedPwd);
        return true;
    }

    // 11. 소셜 로그인 (닉네임 중복 처리 로직 추가됨 ★)
    @Override
    @Transactional
    public MemberDto socialLogin(String email, String name, String provider, String providerId) {
        // 1. DB 조회
        MemberDto member = memberDao.selectMemberByEmail(email);

        // 2. 없으면 자동 회원가입 (GUEST)
        if (member == null) {
            // 생성 및 중복 방지
            String nickname = name; // 기본적으로 소셜 이름을 닉네임으로 사용

            // 만약 닉네임이 이미 존재하면-> 뒤에 랜덤 숫자 붙이기 반복
            // (예: 김싸피 -> 김싸피_1234)
            while (memberDao.checkNickname(nickname) > 0) {
                int randomNum = (int) (Math.random() * 9000) + 1000; // 1000~9999
                nickname = name + "_" + randomNum;
            }

            member = MemberDto.builder()
                    .email(email)
                    .name(name)           // 실명
                    .nickname(nickname)   // 닉네임
                    .provider(provider)
                    .providerId(providerId)
                    .role("ROLE_GUEST")   // 추가 정보 필요
                    .status("ACTIVE")
                    .build();

            // 소셜은 비밀번호 없음 (임의값)
            member.setPassword("SOCIAL_LOGIN");

            memberDao.insertMember(member);

            // 방금 넣은 거 다시 조회 (PK 확보)
            member = memberDao.selectMemberByEmail(email);
        }

        // 3. 로그인 처리
        memberDao.updateLastLogin(member.getUserId());

        return member;
    }

    @Override
    public boolean isLoginIdDuplicate(String loginId) {
        return memberDao.checkLoginId(loginId) > 0;
    }
}
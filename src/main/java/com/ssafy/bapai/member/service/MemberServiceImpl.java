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

    // 1. 회원가입
    @Override
    @Transactional
    public void signup(MemberDto member) {
        String encodedPwd = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPwd);
        memberDao.insertMember(member);
    }

    // 2. 로그인 (아이디로 로그인)
    @Override
    public MemberDto login(String loginId, String password) {
        // [수정] 이메일이 아니라 loginId로 조회
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

    // 3. 회원 조회
    @Override
    public MemberDto getMember(Long userId) {
        return memberDao.selectMemberById(userId);
    }

    // 4. 정보 수정
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

    // [추가] 아이디 중복 체크
    @Override
    public boolean isLoginIdDuplicate(String loginId) {
        return memberDao.checkLoginId(loginId) > 0;
    }

    // 7. 비밀번호 확인
    @Override
    public boolean checkPassword(Long userId, String rawPassword) {
        MemberDto member = memberDao.selectMemberById(userId);
        if (member == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, member.getPassword());
    }

    // 8. 비밀번호 변경
    @Override
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        String encodedPwd = passwordEncoder.encode(newPassword);
        memberDao.updatePassword(userId, encodedPwd);
    }

    // 9. 회원 탈퇴
    @Override
    @Transactional
    public void withdraw(Long userId) {
        memberDao.deleteMember(userId);
    }

    // 10. 아이디 찾기 (이메일 인증 후)
    @Override
    public String findLoginId(String email, String code) {
        if (!emailService.verifyCode(email, code)) {
            throw new IllegalArgumentException("인증번호가 틀렸습니다.");
        }
        MemberDto member = memberDao.selectMemberByEmail(email);
        if (member == null) {
            throw new IllegalArgumentException("가입된 이메일이 없습니다.");
        }
        if (!"LOCAL".equals(member.getProvider())) {
            throw new IllegalArgumentException("소셜 로그인 회원입니다.");
        }

        return member.getLoginId();
    }

    // 11. 비밀번호 재설정 전 본인확인
    @Override
    public boolean verifyUserForReset(String loginId, String email, String code) {
        if (!emailService.verifyCode(email, code)) {
            return false;
        }
        MemberDto member = memberDao.selectMemberByLoginIdAndEmail(loginId, email);
        return member != null && "LOCAL".equals(member.getProvider());
    }

    // 12. 비밀번호 초기화 (재설정)
    @Override
    @Transactional
    public boolean resetPassword(String loginId, String email, String code, String newPassword) {
        if (!verifyUserForReset(loginId, email, code)) {
            return false;
        }

        MemberDto member = memberDao.selectMemberByLoginId(loginId);
        String encodedPwd = passwordEncoder.encode(newPassword);
        memberDao.updatePassword(member.getUserId(), encodedPwd);
        return true;
    }

    // 13. 소셜 로그인 (아이디 자동 생성 추가)
    @Override
    @Transactional
    public MemberDto socialLogin(String email, String name, String provider, String providerId) {
        MemberDto member = memberDao.selectMemberByEmail(email);

        if (member == null) {
            // 1. 닉네임 중복 처리
            String nickname = name;
            while (memberDao.checkNickname(nickname) > 0) {
                int randomNum = (int) (Math.random() * 9000) + 1000;
                nickname = name + "_" + randomNum;
            }

            // 2. [중요] 로그인 아이디(loginId) 자동 생성 (예: kakao_123456)
            String loginId = provider.toLowerCase() + "_" + providerId;
            if (loginId.length() > 50) {
                loginId = loginId.substring(0, 50); // 길이 제한 방지
            }

            member = MemberDto.builder()
                    .email(email)
                    .name(name)
                    .nickname(nickname)
                    .loginId(loginId)     // [추가됨] 이거 없으면 에러남!
                    .provider(provider)
                    .providerId(providerId)
                    .role("ROLE_GUEST")
                    .status("ACTIVE")
                    .build();

            member.setPassword("SOCIAL_LOGIN");
            memberDao.insertMember(member);
            member = memberDao.selectMemberByEmail(email);
        }

        memberDao.updateLastLogin(member.getUserId());
        return member;
    }
}
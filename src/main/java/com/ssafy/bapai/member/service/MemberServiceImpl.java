package com.ssafy.bapai.member.service;

import com.ssafy.bapai.common.redis.RefreshTokenRepository;
import com.ssafy.bapai.member.dao.HealthDao;
import com.ssafy.bapai.member.dao.MemberDao;
import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.OptionDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final HealthDao healthDao;

    // ★ [추가] Redis 리포지토리 주입
    private final RefreshTokenRepository refreshTokenRepository;

    // 1. 회원가입
    @Override
    @Transactional
    public void signup(MemberDto member) {
        String encodedPwd = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPwd);
        member.setProvider("LOCAL");
        member.setStatus("ACTIVE");

        memberDao.insertMember(member);
        Long newUserId = member.getUserId();

        if (member.getWeight() != null) {
            memberDao.insertWeightHistory(newUserId, member.getWeight());
        }

        if (member.getDiseaseIds() != null && !member.getDiseaseIds().isEmpty()) {
            healthDao.insertMemberDiseases(newUserId, member.getDiseaseIds());
        }

        if (member.getAllergyIds() != null && !member.getAllergyIds().isEmpty()) {
            healthDao.insertMemberAllergies(newUserId, member.getAllergyIds());
        }
    }

    // 2. 로그인
    @Override
    public MemberDto login(String username, String password) {
        MemberDto member = memberDao.selectMemberByUsername(username);

        if (member == null || "WITHDRAWN".equals(member.getStatus())) {
            return null;
        }
        if (!passwordEncoder.matches(password, member.getPassword())) {
            return null;
        }

        memberDao.updateLastLogin(member.getUserId());
        return member;
    }

    // ★ [수정] 로그아웃 (DB 삭제 -> Redis 삭제)
    @Override
    @Transactional
    public void logout(Long userId) {
        // 기존 코드: memberDao.deleteRefreshToken(userId);

        // 변경 코드: Redis에서 해당 유저의 리프레시 토큰 삭제
        refreshTokenRepository.deleteById(String.valueOf(userId));
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

    // 7. 아이디 중복 체크
    @Override
    public boolean isUsernameDuplicate(String username) {
        return memberDao.checkUsername(username) > 0;
    }

    // 8. 비밀번호 확인
    @Override
    public boolean checkPassword(Long userId, String rawPassword) {
        MemberDto member = memberDao.selectMemberById(userId);
        if (member == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, member.getPassword());
    }

    // 9. 비밀번호 변경
    @Override
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        MemberDto member = memberDao.selectMemberById(userId);

        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 사용 중인 비밀번호입니다.");
        }

        String encodedPwd = passwordEncoder.encode(newPassword);
        memberDao.updatePassword(userId, encodedPwd);
    }

    // 10. 회원 탈퇴
    @Override
    @Transactional
    public void withdraw(Long userId) {
        memberDao.deleteMember(userId);
    }

    // 11. 아이디 찾기
    @Override
    public String findUsername(String email, String code) {
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
        return member.getUsername();
    }

    // 12. 비밀번호 재설정 전 본인확인
    @Override
    public boolean verifyUserForReset(String username, String email, String code) {
        if (!emailService.verifyCode(email, code)) {
            return false;
        }
        MemberDto member = memberDao.selectMemberByUsernameAndEmail(username, email);
        return member != null && "LOCAL".equals(member.getProvider());
    }

    // 13. 비밀번호 재설정
    @Override
    public boolean resetPassword(String username, String email, String code) {
        if (!verifyUserForReset(username, email, code)) {
            return false;
        }

        String tempPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        MemberDto member = memberDao.selectMemberByUsername(username);
        String encodedPwd = passwordEncoder.encode(tempPassword);

        member.setPassword(encodedPwd);
        member.setTempPassword(true);
        memberDao.updateTempPassword(member);

        emailService.sendTempPassword(email, tempPassword);
        return true;
    }

    // 14. 소셜 로그인
    @Override
    @Transactional
    public MemberDto socialLogin(String email, String name, String provider, String providerId) {
        MemberDto member = memberDao.selectMemberByEmail(email);

        if (member == null) {
            String nickname = name;
            while (memberDao.checkNickname(nickname) > 0) {
                int randomNum = (int) (Math.random() * 9000) + 1000;
                nickname = name + "_" + randomNum;
            }

            String username = provider.toLowerCase() + "_" + providerId;
            if (username.length() > 50) {
                username = username.substring(0, 50);
            }

            member = MemberDto.builder()
                    .email(email)
                    .name(name)
                    .nickname(nickname)
                    .username(username)
                    .provider(provider)
                    .providerId(providerId)
                    .role("ROLE_GUEST")
                    .status("ACTIVE")
                    .isTempPassword(false).build();

            member.setPassword("SOCIAL_LOGIN");

            memberDao.insertMember(member);
            member = memberDao.selectMemberByEmail(email);
        }

        memberDao.updateLastLogin(member.getUserId());
        return member;
    }

    @Override
    public Map<String, List<OptionDto>> getHealthOptions() {
        Map<String, List<OptionDto>> options = new HashMap<>();
        options.put("diseases", healthDao.selectAllDiseases());
        options.put("allergies", healthDao.selectAllAllergies());
        return options;
    }
}
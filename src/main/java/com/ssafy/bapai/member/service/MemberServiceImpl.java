package com.ssafy.bapai.member.service;

import com.ssafy.bapai.common.redis.RefreshTokenRepository;
import com.ssafy.bapai.member.dao.HealthDao;
import com.ssafy.bapai.member.dao.MemberDao;
import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.OptionDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        // 유저 없음 or 탈퇴
        if (member == null || "WITHDRAWN".equals(member.getStatus())) {
            return null;
        }

        // 비밀번호 체크
        if (!passwordEncoder.matches(password, member.getPassword())) {
            return null;
        }

        memberDao.updateLastLogin(member.getUserId());
        return member;
    }

    // 로그아웃
    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteById(String.valueOf(userId));
    }

    // 3. 회원 조회
    @Override
    public MemberDto getMember(Long userId) {
        // 1. 기본 회원 정보 가져오기
        MemberDto member = memberDao.selectMemberById(userId);

        if (member != null) {
            // 2. 질환 ID 목록 가져와서 넣기
            List<Integer> diseaseIds = healthDao.selectDiseaseIdsByUserId(userId);
            member.setDiseaseIds(diseaseIds);

            // 3. 알레르기 ID 목록 가져와서 넣기
            List<Integer> allergyIds = healthDao.selectAllergyIdsByUserId(userId);
            member.setAllergyIds(allergyIds);
        }

        return member;
    }

    // 4. 정보 수정 (질환/알레르기 로직 추가됨)
    @Override
    @Transactional
    public void updateMember(MemberDto member) {
        Long userId = member.getUserId();

        // 1. 기본 정보(키, 몸무게, 활동량, 이름 등) 수정
        memberDao.updateMember(member);

        // 2. 질환(Diseases) 수정 로직
        // (프론트에서 diseaseIds 키 자체가 안 왔으면 null -> 수정 안함)
        // (빈 리스트 [] 가 왔으면 -> 모두 삭제로 처리)
        if (member.getDiseaseIds() != null) {
            healthDao.deleteMemberDiseases(userId); // 일단 기존 것 삭제
            if (!member.getDiseaseIds().isEmpty()) {
                healthDao.insertMemberDiseases(userId, member.getDiseaseIds()); // 새 목록 등록
            }
        }

        // 3. 알레르기(Allergies) 수정 로직
        if (member.getAllergyIds() != null) {
            healthDao.deleteMemberAllergies(userId); // 일단 기존 것 삭제
            if (!member.getAllergyIds().isEmpty()) {
                healthDao.insertMemberAllergies(userId, member.getAllergyIds()); // 새 목록 등록
            }
        }

        // 4. (선택) TDEE 재계산 로직이 있다면 여기에 추가
        // 추후에 추가 예정
    }


    @Override
    public boolean isEmailDuplicate(String email) {
        return memberDao.checkEmail(email) > 0;
    }

    @Override
    public boolean isNicknameDuplicate(String nickname) {
        return memberDao.checkNickname(nickname) > 0;
    }

    @Override
    public boolean isUsernameDuplicate(String username) {
        return memberDao.checkUsername(username) > 0;
    }

    @Override
    public boolean checkPassword(Long userId, String rawPassword) {
        MemberDto member = memberDao.selectMemberById(userId);
        if (member == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, member.getPassword());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        MemberDto member = memberDao.selectMemberById(userId);
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 사용 중인 비밀번호입니다.");
        }
        memberDao.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        memberDao.deleteMember(userId);
    }

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

    @Override
    public boolean verifyUserForReset(String username, String email, String code) {
        if (!emailService.verifyCode(email, code)) {
            return false;
        }
        MemberDto member = memberDao.selectMemberByUsernameAndEmail(username, email);
        return member != null && "LOCAL".equals(member.getProvider());
    }

    @Override
    public boolean resetPassword(String username, String email, String code) {
        if (!verifyUserForReset(username, email, code)) {
            return false;
        }
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);
        MemberDto member = memberDao.selectMemberByUsername(username);
        member.setPassword(passwordEncoder.encode(tempPassword));
        member.setTempPassword(true);
        memberDao.updateTempPassword(member);
        emailService.sendTempPassword(email, tempPassword);
        return true;
    }

    @Override
    @Transactional
    public MemberDto socialLogin(String email, String name, String provider, String providerId) {
        MemberDto member = memberDao.selectMemberByEmail(email);
        if (member == null) {
            String nickname = name;
            while (memberDao.checkNickname(nickname) > 0) {
                nickname = name + "_" + ((int) (Math.random() * 9000) + 1000);
            }
            String username = (provider.toLowerCase() + "_" + providerId);
            if (username.length() > 50) {
                username = username.substring(0, 50);
            }

            member = MemberDto.builder()
                    .email(email).name(name).nickname(nickname).username(username)
                    .provider(provider).providerId(providerId)
                    .role("ROLE_GUEST").status("ACTIVE").isTempPassword(false)
                    .build();
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
package com.ssafy.bapai.member.service;

import com.ssafy.bapai.member.dao.EmailAuthDao;
import com.ssafy.bapai.member.dto.EmailAuthDto;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;
    private final EmailAuthDao emailAuthDao;

    @Override
    @Transactional
    public void sendVerificationCode(String email) {
        // 1. 6자리 난수 생성
        String code = String.valueOf((int) (Math.random() * 899999) + 100000);

        // 2. DB 저장 (유효시간 5분)
        EmailAuthDto authDto = EmailAuthDto.builder()
                .email(email)
                .authCode(code)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .isVerified(false).build();

        emailAuthDao.save(authDto);

        // 3. 이메일 전송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[냠냠코치] 인증번호 안내");
        message.setText("인증번호는 [" + code + "] 입니다.\n5분 안에 입력해주세요.");

        javaMailSender.send(message);
    }

    @Override
    @Transactional
    public boolean verifyCode(String email, String code) {
        // 1. DB 조회
        EmailAuthDto authDto = emailAuthDao.findRecentByEmail(email);

        // 2. 검증 로직
        if (authDto == null) {
            return false; // 기록 없음
        }
        if (authDto.getExpiredAt().isBefore(LocalDateTime.now())) {
            return false; // 시간 만료
        }
        if (!authDto.getAuthCode().equals(code)) {
            return false; // 번호 불일치
        }

        // 3. 인증 성공 처리
        emailAuthDao.updateVerified(authDto.getAuthId());
        return true;
    }


    @Override
    public void sendTempPassword(String email, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[냠냠코치] 임시 비밀번호 발급 안내");
        message.setText("회원님의 임시 비밀번호는 [" + tempPassword + "] 입니다.\n로그인 후 반드시 비밀번호를 변경해주세요.");
        javaMailSender.send(message);
    }
}
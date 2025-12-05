package com.ssafy.bapai.member.service;

public interface EmailService {
    // 인증번호 발송
    void sendVerificationCode(String email);

    // 인증번호 검증 (맞으면 true)
    boolean verifyCode(String email, String code);

    void sendTempPassword(String email, String tempPassword);
    
}
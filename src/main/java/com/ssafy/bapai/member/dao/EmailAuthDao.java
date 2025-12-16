package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.EmailAuthDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailAuthDao {
    // 인증번호 저장
    int save(EmailAuthDto emailAuthDto);

    // 이메일로 가장 최근 인증정보 조회
    EmailAuthDto findRecentByEmail(String email);

    // 인증 성공 처리 (is_verified = true)
    int updateVerified(Long authId);

}
package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.RefreshTokenDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenDao {
    void save(RefreshTokenDto token);

    String findToken(String rtKey);

    void delete(String rtKey);

    // 만료된 토큰 일괄 삭제
    int deleteExpiredTokens();
}
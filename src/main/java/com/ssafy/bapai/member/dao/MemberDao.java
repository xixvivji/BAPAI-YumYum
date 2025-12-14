package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.MemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberDao {
    int insertMember(MemberDto member);

    // LoginId -> Username
    MemberDto selectMemberByUsername(String username);

    MemberDto selectMemberByEmail(String email);

    MemberDto selectMemberById(Long userId);

    MemberDto selectMemberByUsernameAndEmail(@Param("username") String username,
                                             @Param("email") String email);

    int checkEmail(String email);

    int checkNickname(String nickname);

    int checkUsername(String username);

    int updateMember(MemberDto member);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updateLastLogin(Long userId);

    int deleteMember(Long userId);

    int updateTempPassword(MemberDto member);

    void deleteRefreshToken(Long userId);

    // 회원가입 시 초기 몸무게 기록용
    void insertWeightHistory(@Param("userId") Long userId, @Param("weight") double weight);

    // 영양 분석 서비스에서 호출하는 메서드
    MemberDto selectMemberDetail(Long userId);
}
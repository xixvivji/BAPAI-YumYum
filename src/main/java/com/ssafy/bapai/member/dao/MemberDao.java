package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.MemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberDao {
    int insertMember(MemberDto member);

    // [수정] LoginId -> Username
    MemberDto selectMemberByUsername(String username);

    MemberDto selectMemberByEmail(String email);

    MemberDto selectMemberById(Long userId);

    // [수정] LoginId -> Username
    MemberDto selectMemberByUsernameAndEmail(@Param("username") String username,
                                             @Param("email") String email);

    int checkEmail(String email);

    int checkNickname(String nickname);

    // [수정] LoginId -> Username
    int checkUsername(String username);

    int updateMember(MemberDto member);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updateLastLogin(Long userId);

    int deleteMember(Long userId);

    int updateTempPassword(MemberDto member);
}
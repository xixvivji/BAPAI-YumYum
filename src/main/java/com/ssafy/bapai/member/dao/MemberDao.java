package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.MemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberDao {

    // 회원가입
    int insertMember(MemberDto member);

    // 조회
    MemberDto selectMemberByLoginId(String loginId);

    MemberDto selectMemberByEmail(String email);

    MemberDto selectMemberById(Long userId);

    MemberDto selectMemberByLoginIdAndEmail(@Param("loginId") String loginId,
                                            @Param("email") String email);

    int checkLoginId(String loginId);

    // 중복 체크
    int checkEmail(String email);


    // 닉네임 중복체크로 변경
    int checkNickname(String nickname);

    // 정보 수정
    int updateMember(MemberDto member);

    // 비밀번호 변경 (@Param 필수)
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    // 로그인 갱신
    int updateLastLogin(Long userId);

    // 탈퇴
    int deleteMember(Long userId);
}
package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.OptionDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthDao {
    // 옵션 목록 조회
    List<OptionDto> selectAllDiseases();

    List<OptionDto> selectAllAllergies();

    // 회원별 정보 저장
    void insertMemberDiseases(@Param("userId") Long userId,
                              @Param("diseaseIds") List<Integer> diseaseIds);

    void insertMemberAllergies(@Param("userId") Long userId,
                               @Param("allergyIds") List<Integer> allergyIds);
}
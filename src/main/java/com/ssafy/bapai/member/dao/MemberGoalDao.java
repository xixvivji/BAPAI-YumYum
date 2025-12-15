package com.ssafy.bapai.member.dao;

import com.ssafy.bapai.member.dto.MemberGoalDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberGoalDao {
    // 목표 설정 (없으면 insert)
    void insertGoal(MemberGoalDto goalDto);

    // 목표 수정 (있으면 update)
    void updateGoal(MemberGoalDto goalDto);

    // 이미 분석된 정보가 있는지 확인
    int checkExist(Long userId);

    // 내 목표 조회
    MemberGoalDto selectGoalByUserId(Long userId);
}
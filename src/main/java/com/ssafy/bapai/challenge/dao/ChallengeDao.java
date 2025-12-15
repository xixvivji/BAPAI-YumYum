package com.ssafy.bapai.challenge.dao;

import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChallengeDao {
    // 1. 챌린지 관리
    void insertChallenge(ChallengeDto dto);

    List<ChallengeDto> selectListByGroupId(Long groupId);

    ChallengeDto selectDetail(Long challengeId);

    // 2. 챌린지 멤버 (참여/진행)
    void joinChallenge(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    int checkJoined(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    void increaseCurrentCount(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    void updateStatusSuccess(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    int selectCurrentCount(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    // 3. 식단 기록
    void insertMealLog(MealLogDto dto);

    // 5. 프리셋 (추천)
    List<ChallengePresetDto> selectPresetsByKeywords(@Param("keywords") List<String> keywords);

    ChallengePresetDto selectPresetById(Long presetId);
}
package com.ssafy.bapai.challenge.dao;

import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import com.ssafy.bapai.challenge.dto.ReportLogDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChallengeDao {
    // 1. 챌린지 관리
    void insertChallenge(ChallengeDto dto);

    List<ChallengeDto> selectListByTeamId(Long teamId);

    ChallengeDto selectDetail(Long challengeId);

    // 2. 챌린지 멤버 (참여/진행)
    void joinChallenge(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    int checkJoined(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    // 진행도 업데이트 (핵심!)
    void increaseCurrentCount(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    void updateStatusSuccess(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    // 멤버 상태 조회
    int selectCurrentCount(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    // 3. 식단 기록 (MealLog)
    void insertMealLog(MealLogDto dto);

    List<MealLogDto> selectMealListByChallenge(Long challengeId);

    List<MealLogDto> selectMealListByUser(Long userId);

    // 4. 리포트 로그 (통계)
    void insertReportLog(ReportLogDto dto);
}
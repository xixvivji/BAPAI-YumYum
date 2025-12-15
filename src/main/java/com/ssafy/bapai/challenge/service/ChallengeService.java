package com.ssafy.bapai.challenge.service;

import com.ssafy.bapai.challenge.dao.ChallengeDao;
import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeDao challengeDao;

    // 1. 챌린지 생성
    @Transactional
    public void createChallenge(ChallengeDto dto) {
        // dto.getGroupId()가 Mapper로 전달됨
        challengeDao.insertChallenge(dto);
    }

    // 2. 챌린지 목록 조회 (groupId 기준)
    public List<ChallengeDto> getList(Long groupId) {
        return challengeDao.selectListByGroupId(groupId);
    }

    // 3. 챌린지 참여
    @Transactional
    public void joinChallenge(Long challengeId, Long userId) {
        if (challengeDao.checkJoined(challengeId, userId) > 0) {
            throw new IllegalStateException("이미 참여 중인 챌린지입니다.");
        }
        challengeDao.joinChallenge(challengeId, userId);
    }

    // 4. 식단 기록 & 챌린지 자동 업데이트
    @Transactional
    public void recordMeal(MealLogDto mealDto) {
        simulateAIAnalysis(mealDto); // AI 분석 시뮬레이션
        challengeDao.insertMealLog(mealDto); // 기록 저장

        if (mealDto.getChallengeId() != null) {
            updateChallengeProgress(mealDto.getChallengeId(), mealDto.getUserId(),
                    mealDto.getScore());
        }
    }

    // 5. 추천 챌린지(프리셋) 조회
    public List<ChallengePresetDto> getRecommendChallenges(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return challengeDao.selectPresetsByKeywords(List.of("일반"));
        }
        return challengeDao.selectPresetsByKeywords(keywords);
    }

    // --- 내부 로직 ---
    private void updateChallengeProgress(Long challengeId, Long userId, int mealScore) {
        ChallengeDto challenge = challengeDao.selectDetail(challengeId);
        if (challenge == null) {
            return;
        }

        if (challenge.getMinScore() > 0 && mealScore < challenge.getMinScore()) {
            return;
        }

        challengeDao.increaseCurrentCount(challengeId, userId);

        int currentCount = challengeDao.selectCurrentCount(challengeId, userId);
        if (currentCount >= challenge.getTargetCount()) {
            challengeDao.updateStatusSuccess(challengeId, userId);
        }
    }

    // 임시 무조건 되게
    private void simulateAIAnalysis(MealLogDto dto) {
        dto.setMenuName("AI 분석: 닭가슴살 샐러드");
        dto.setCalories(320.0);
        dto.setCarbs(20.0);
        dto.setProtein(28.0);
        dto.setFat(8.0);
        dto.setScore((int) (Math.random() * 31) + 70);
    }
}
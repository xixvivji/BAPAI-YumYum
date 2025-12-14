package com.ssafy.bapai.challenge.service;

import com.ssafy.bapai.challenge.dao.ChallengeDao;
import com.ssafy.bapai.challenge.dto.ChallengeDto;
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
        challengeDao.insertChallenge(dto);
    }

    // 2. 챌린지 목록
    public List<ChallengeDto> getList(Long teamId) {
        return challengeDao.selectListByTeamId(teamId);
    }

    // 3. 챌린지 참여
    @Transactional
    public void joinChallenge(Long challengeId, Long userId) {
        if (challengeDao.checkJoined(challengeId, userId) > 0) {
            throw new IllegalStateException("이미 참여 중");
        }
        challengeDao.joinChallenge(challengeId, userId);
    }

    // 4. 식단 기록 & 챌린지 자동 업데이트 (★ 핵심 로직)
    @Transactional
    public void recordMeal(MealLogDto mealDto) {
        // (1) AI 분석 시뮬레이션 (나중에 진짜 AI로 교체)
        simulateAIAnalysis(mealDto);

        // (2) 식단 기록 저장 (meal_log)
        challengeDao.insertMealLog(mealDto);

        // (3) 만약 챌린지 인증용이라면? -> 진행도 체크!
        if (mealDto.getChallengeId() != null) {
            updateChallengeProgress(mealDto.getChallengeId(), mealDto.getUserId(),
                    mealDto.getScore());
        }
    }

    // 진행도 체크 및 성공 처리 로직
    private void updateChallengeProgress(Long challengeId, Long userId, int mealScore) {
        // 챌린지 정보 가져오기 (목표 확인용)
        ChallengeDto challenge = challengeDao.selectDetail(challengeId);

        // 점수 조건 확인 (예: 60점 이상만 인정)
        if (challenge.getMinScore() > 0 && mealScore < challenge.getMinScore()) {
            return; // 점수 미달로 카운트 안 함
        }

        // 카운트 증가 (+1)
        challengeDao.increaseCurrentCount(challengeId, userId);

        // 목표 달성했는지 확인
        int currentCount = challengeDao.selectCurrentCount(challengeId, userId);
        if (currentCount >= challenge.getTargetCount()) {
            // 축하합니다! 성공 처리
            challengeDao.updateStatusSuccess(challengeId, userId);
        }
    }

    // 임시 AI 분석기
    private void simulateAIAnalysis(MealLogDto dto) {
        dto.setMenuName("건강한 닭가슴살 샐러드");
        dto.setCalories(320.0);
        dto.setCarbs(20.0);
        dto.setProtein(28.0);
        dto.setFat(8.0);

        // 70~100점 랜덤
        dto.setScore((int) (Math.random() * 31) + 70);
    }
}
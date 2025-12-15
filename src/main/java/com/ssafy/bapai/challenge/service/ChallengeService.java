package com.ssafy.bapai.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.challenge.dao.ChallengeDao;
import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeDao challengeDao;
    private final com.ssafy.bapai.diet.service.GeminiService geminiService;
    private final ObjectMapper objectMapper;

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

    // AI 방식실시간 생성 추천 (느림)
    public List<ChallengePresetDto> getAiChallenges(List<String> keywords) {
        // (1) Gemini 호출
        String jsonResult = geminiService.recommendChallengesByAI(keywords);

        // (2) String -> List<ChallengePresetDto> 변환
        try {
            // JSON 배열 문자열을 자바 객체 배열로 변환
            ChallengePresetDto[] array =
                    objectMapper.readValue(jsonResult, ChallengePresetDto[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            e.printStackTrace();
            // 파싱 실패 시 빈 리스트 반환 (서버 안 죽게 방어)
            return Collections.emptyList();
        }
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
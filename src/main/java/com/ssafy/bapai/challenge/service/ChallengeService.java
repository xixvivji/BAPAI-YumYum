package com.ssafy.bapai.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.challenge.dao.ChallengeDao;
import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.challenge.dto.ChallengeSelectRequest;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import com.ssafy.bapai.group.dao.GroupDao;
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
    private final GroupDao groupDao; // ★ 그룹 태그 조회를 위해 추가 주입
    private final com.ssafy.bapai.diet.service.GeminiService geminiService;
    private final ObjectMapper objectMapper;

    // 1. 프리셋 기반 챌린지 생성 (유효성 검사 포함)
    @Transactional
    public void createChallengeFromPreset(Long groupId, ChallengeSelectRequest req) {
        // (1) 프리셋 정보 가져오기
        ChallengePresetDto preset = challengeDao.selectPresetById(req.getPresetId());
        if (preset == null) {
            throw new IllegalArgumentException("존재하지 않는 프리셋입니다.");
        }

        // (2) 그룹의 태그(키워드) 목록 가져오기
        List<String> groupTags = groupDao.selectGroupTags(groupId);

        // (3) 유효성 검사: "일반" 이거나 그룹 태그에 포함되어야 함
        String presetKeyword = preset.getKeyword(); // 예: "다이어트" or "일반"

        boolean isCommon = "일반".equals(presetKeyword);
        boolean isMatched = groupTags != null && groupTags.contains(presetKeyword);

        if (!isCommon && !isMatched) {
            throw new IllegalArgumentException(
                    "우리 그룹의 주제(" + groupTags + ")와 맞지 않는 챌린지입니다.");
        }

        // (4) 챌린지 생성 및 저장
        ChallengeDto newChallenge = new ChallengeDto();
        newChallenge.setGroupId(groupId);
        newChallenge.setTitle(preset.getTitle());
        newChallenge.setContent(preset.getContent());
        newChallenge.setGoalType(preset.getGoalType());
        newChallenge.setTargetCount(preset.getTargetCount());
        newChallenge.setMinScore(preset.getTargetCount() > 0 ? 0 : 60); // 예시: 점수형이면 기본값 설정

        newChallenge.setStartDate(req.getStartDate());
        newChallenge.setEndDate(req.getEndDate());

        challengeDao.insertChallenge(newChallenge);
    }

    // 2. 챌린지 목록 조회
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

    // 4. 식단 기록 & 업데이트
    @Transactional
    public void recordMeal(MealLogDto mealDto) {
        simulateAIAnalysis(mealDto);
        challengeDao.insertMealLog(mealDto);

        if (mealDto.getChallengeId() != null) {
            updateChallengeProgress(mealDto.getChallengeId(), mealDto.getUserId(),
                    mealDto.getScore());
        }
    }

    // 5. 추천 챌린지(DB)
    public List<ChallengePresetDto> getRecommendChallenges(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return challengeDao.selectPresetsByKeywords(List.of("일반"));
        }
        return challengeDao.selectPresetsByKeywords(keywords);
    }

    // 6. 추천 챌린지(AI)
    public List<ChallengePresetDto> getAiChallenges(List<String> keywords) {
        String jsonResult = geminiService.recommendChallengesByAI(keywords);
        try {
            ChallengePresetDto[] array =
                    objectMapper.readValue(jsonResult, ChallengePresetDto[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
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

    private void simulateAIAnalysis(MealLogDto dto) {
        dto.setMenuName("AI 분석: 닭가슴살 샐러드");
        dto.setCalories(320.0);
        dto.setCarbs(20.0);
        dto.setProtein(28.0);
        dto.setFat(8.0);
        dto.setScore((int) (Math.random() * 31) + 70);
    }
}
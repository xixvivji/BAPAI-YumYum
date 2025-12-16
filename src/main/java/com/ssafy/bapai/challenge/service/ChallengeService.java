package com.ssafy.bapai.challenge.service;

import com.ssafy.bapai.challenge.dao.ChallengeDao;
import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.challenge.dto.ChallengeSelectRequest;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import com.ssafy.bapai.group.dao.GroupDao;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeDao challengeDao;
    private final GroupDao groupDao;

    // 1. 프리셋 기반 챌린지 생성
    @Transactional
    public void createChallengeFromPreset(Long groupId, ChallengeSelectRequest req) {
        ChallengePresetDto preset = challengeDao.selectPresetById(req.getPresetId());
        if (preset == null) {
            throw new IllegalArgumentException("존재하지 않는 프리셋입니다.");
        }

        List<String> groupTags = groupDao.selectGroupTags(groupId);
        String presetKeyword = preset.getKeyword();

        boolean isCommon = "일반".equals(presetKeyword);
        boolean isMatched = groupTags != null && groupTags.contains(presetKeyword);

        if (!isCommon && !isMatched) {
            throw new IllegalArgumentException("우리 그룹 주제와 맞지 않습니다.");
        }

        ChallengeDto newChallenge = new ChallengeDto();
        newChallenge.setGroupId(groupId);
        newChallenge.setTitle(preset.getTitle());
        newChallenge.setContent(preset.getContent());
        newChallenge.setGoalType(preset.getGoalType());
        newChallenge.setTargetCount(preset.getTargetCount());
        newChallenge.setMinScore(preset.getTargetCount() > 0 ? 0 : 60);
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
        // simulateAIAnalysis(mealDto); // 이건 더미 데이터 넣는 거라 필요하면 남겨두고, 아니면 삭제

        challengeDao.insertMealLog(mealDto); // DB 저장

        if (mealDto.getChallengeId() != null) {
            updateChallengeProgress(mealDto.getChallengeId(), mealDto.getUserId(),
                    mealDto.getScore());
        }
    }

    // 5. 추천 챌린지 (DB에 있는 프리셋만 검색) - AI 아님
    public List<ChallengePresetDto> getRecommendChallenges(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return challengeDao.selectPresetsByKeywords(List.of("일반"));
        }
        return challengeDao.selectPresetsByKeywords(keywords);
    }

    // ❌ getAiChallenges() 메서드 삭제됨 (AiService로 이동)

    // --- 내부 로직 ---
    private void updateChallengeProgress(Long challengeId, Long userId, int mealScore) {
        ChallengeDto challenge = challengeDao.selectDetail(challengeId);
        if (challenge == null) {
            return;
        }

        // 점수 조건 체크 (목표 점수보다 낮으면 카운트 안 함)
        if (challenge.getMinScore() > 0 && mealScore < challenge.getMinScore()) {
            return;
        }

        challengeDao.increaseCurrentCount(challengeId, userId);

        int currentCount = challengeDao.selectCurrentCount(challengeId, userId);
        if (currentCount >= challenge.getTargetCount()) {
            challengeDao.updateStatusSuccess(challengeId, userId);
        }
    }
}
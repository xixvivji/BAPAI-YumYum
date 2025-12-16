package com.ssafy.bapai.challenge.controller;

import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.challenge.dto.ChallengeSelectRequest;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import com.ssafy.bapai.challenge.service.ChallengeService;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "8. Group Challenge & Meal", description = "모임 챌린지 및 식단 인증")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final JwtUtil jwtUtil;

    // 1. 챌린지 관리 (생성 및 조회)
    @PostMapping("/groups/{groupId}/challenges/preset")
    @Operation(summary = "프리셋으로 챌린지 생성", description = "그룹의 태그와 일치하거나 '일반' 챌린지만 생성 가능합니다.")
    public ResponseEntity<?> createChallengeFromPreset(
            @PathVariable Long groupId,
            @RequestBody ChallengeSelectRequest request) {

        challengeService.createChallengeFromPreset(groupId, request);
        return ResponseEntity.ok("챌린지가 등록되었습니다.");
    }

    @GetMapping("/groups/{groupId}/challenges")
    @Operation(summary = "챌린지 목록 조회")
    public ResponseEntity<List<ChallengeDto>> listChallenges(@PathVariable Long groupId) {
        return ResponseEntity.ok(challengeService.getList(groupId));
    }

    @PostMapping("/challenges/{challengeId}/join")
    @Operation(summary = "챌린지 참여하기")
    public ResponseEntity<?> joinChallenge(
            @PathVariable Long challengeId,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtUtil.getUserId(token.substring(7));
        challengeService.joinChallenge(challengeId, userId);
        return ResponseEntity.ok("참여 완료!");
    }


    // 2. 식단 인증 (Meal Log)
    @PostMapping("/meals")
    @Operation(summary = "식단 기록 (챌린지 인증 포함)", description = "challengeId가 있으면 챌린지 카운트 반영")
    public ResponseEntity<?> recordMeal(
            @RequestHeader("Authorization") String token,
            @RequestBody MealLogDto dto) {

        Long userId = jwtUtil.getUserId(token.substring(7));
        dto.setUserId(userId);

        challengeService.recordMeal(dto);

        return ResponseEntity.ok("식단이 기록되고 점수가 반영되었습니다.");
    }


    // 3. 챌린지 추천 (DB 프리셋만 남김)
    @GetMapping("/challenges/recommend/presets")
    @Operation(summary = "추천 챌린지 (DB 프리셋)", description = "서버에 저장된 기본 추천 목록을 빠르게 반환합니다.")
    public ResponseEntity<List<ChallengePresetDto>> getPresets(
            @Parameter(description = "관심 키워드 (예: 다이어트, 근력)", required = false)
            @RequestParam(required = false) List<String> keywords) {
        return ResponseEntity.ok(challengeService.getRecommendChallenges(keywords));
    }

    
}
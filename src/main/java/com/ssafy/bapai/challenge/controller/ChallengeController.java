package com.ssafy.bapai.challenge.controller;

import com.ssafy.bapai.challenge.dto.ChallengeDto;
import com.ssafy.bapai.challenge.dto.MealLogDto;
import com.ssafy.bapai.challenge.service.ChallengeService;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "05. Challenge & Meal", description = "팀 챌린지 및 식단 인증")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final JwtUtil jwtUtil;

    // --- 챌린지 관련 ---

    @PostMapping("/teams/{teamId}/challenges")
    @Operation(summary = "챌린지 생성", description = "goalType(COUNT), targetCount(목표횟수) 필수")
    public ResponseEntity<?> createChallenge(
            @PathVariable Long teamId,
            @RequestBody ChallengeDto dto) {
        dto.setTeamId(teamId);
        challengeService.createChallenge(dto);
        return ResponseEntity.ok("챌린지가 생성되었습니다.");
    }

    @GetMapping("/teams/{teamId}/challenges")
    @Operation(summary = "챌린지 목록 조회")
    public ResponseEntity<List<ChallengeDto>> listChallenges(@PathVariable Long teamId) {
        return ResponseEntity.ok(challengeService.getList(teamId));
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

    // --- 식단 인증(Meal Log) 관련 ---

    @PostMapping("/meals")
    @Operation(summary = "식단 기록 (챌린지 인증 포함)", description = "challengeId를 넣으면 해당 챌린지의 카운트가 올라갑니다.")
    public ResponseEntity<?> recordMeal(
            @RequestHeader("Authorization") String token,
            @RequestBody MealLogDto dto) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        dto.setUserId(userId);

        challengeService.recordMeal(dto); // 여기서 AI 분석 -> 저장 -> 챌린지 카운트까지 다 처리됨

        return ResponseEntity.ok("식단이 기록되고 점수가 반영되었습니다.");
    }
}
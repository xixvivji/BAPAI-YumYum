package com.ssafy.bapai.ai.controller;

import com.ssafy.bapai.ai.dto.AiReportResponse;
import com.ssafy.bapai.ai.service.AiService;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "AI 서비스 API", description = "이미지 분석, 메뉴/챌린지 추천, 리포트(일간/주간/월간) 기능")
public class AiRestController {

    private final AiService aiService;
    private final JwtUtil jwtUtil;

    // 1. 식단 이미지 분석
    @Operation(summary = "식단 이미지 분석", description = "사진을 보내면 영양 정보를 분석해줍니다.")
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeImage(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(aiService.analyzeImage(file));
    }

    // 2. [추가] 다음 끼니 추천 (DietController에서 가져옴)
    @Operation(summary = "다음 끼니 추천", description = "오늘 먹은 식단을 분석하여 부족한 영양소를 채울 메뉴를 추천합니다.")
    @GetMapping("/recommend")
    public ResponseEntity<?> recommendNextMeal(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);
        return ResponseEntity.ok(aiService.recommendNextMeal(userId));
    }

    // 3. [추가] AI 챌린지 추천 (ChallengeService에서 가져옴)
    @Operation(summary = "AI 챌린지 추천", description = "관심사 키워드(예: 다이어트, 근력)를 입력하면 챌린지 주제를 추천해줍니다.")
    @GetMapping("/challenges/recommend")
    public ResponseEntity<List<ChallengePresetDto>> recommendChallenges(
            @Parameter(description = "관심 키워드 리스트 (예: ?keywords=다이어트&keywords=운동)")
            @RequestParam(required = false) List<String> keywords) {
        return ResponseEntity.ok(aiService.recommendGroupChallenges(keywords));
    }

    // 4. 일간 리포트 조회
    @Operation(summary = "일간 리포트 조회", description = "오늘의 식단 요약과 AI 조언을 반환합니다. (DB 캐싱 적용)")
    @GetMapping("/report/daily")
    public ResponseEntity<AiReportResponse> getDailyReport(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String date) {
        Long userId = jwtUtil.getUserId(token);
        return ResponseEntity.ok(aiService.getDailyReport(userId, date));
    }

    // 5. 주간 리포트 조회
    @Operation(summary = "주간 리포트 조회", description = "최근 1주일간의 점수 흐름과 AI 총평을 반환합니다.")
    @GetMapping("/report/weekly")
    public ResponseEntity<AiReportResponse> getWeeklyReport(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);
        return ResponseEntity.ok(aiService.getPeriodReport(userId, "WEEKLY"));
    }

    // 6. 월간 리포트 조회
    @Operation(summary = "월간 리포트 조회", description = "최근 1달간의 점수 흐름과 AI 총평을 반환합니다.")
    @GetMapping("/report/monthly")
    public ResponseEntity<AiReportResponse> getMonthlyReport(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);
        return ResponseEntity.ok(aiService.getPeriodReport(userId, "MONTHLY"));
    }
}
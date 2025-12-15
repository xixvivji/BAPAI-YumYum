package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import com.ssafy.bapai.diet.service.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/diet-logs")
@RequiredArgsConstructor
@Tag(name = "2. 식단(Diet) API", description = "식단 기록, 조회, AI 분석, 추천 등")
public class DietRestController {

    private final DietService dietService;
    private final GeminiService geminiService; // [추가] AI 서비스 주입
    private final JwtUtil jwtUtil;

    // 1. 식단 이미지 AI 분석 (Gemini 연동 완료)
    @Operation(summary = "식단 이미지 AI 분석", description = "식단 사진을 업로드하면 Google Gemini가 음식 종류와 칼로리를 분석해서 반환합니다.")
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeDiet(
            @Parameter(description = "업로드할 식단 이미지 파일 (.jpg, .png)", required = true)
            @RequestPart("file") MultipartFile file) {

        // ★ [수정] 가짜 데이터 대신 진짜 Gemini 서비스 호출
        String analysisResult = geminiService.analyzeImage(file);

        // JSON 문자열을 그대로 반환 (프론트엔드에서 파싱해서 사용)
        return ResponseEntity.ok(analysisResult);
    }

    // 2. 식단 기록 저장 (최종)
    @Operation(summary = "식단 기록 저장", description = "AI 분석 후 사용자가 확정한 식단 정보를 DB에 저장합니다.")
    @PostMapping
    public ResponseEntity<?> createDiet(@RequestHeader("Authorization") String token,
                                        @RequestBody DietDto dietDto) {
        Long userId = jwtUtil.getUserId(token);
        dietDto.setUserId(userId);

        dietService.saveDiet(dietDto);

        return ResponseEntity.ok(Map.of(
                "message", "식단이 저장되었습니다.",
                "logId", dietDto.getDietId(),
                "score", 85 // (추후 영양소 점수 계산 로직 추가 가능)
        ));
    }

    // 3. 식단 목록 조회 (월/일)
    @Operation(summary = "식단 목록 조회 (전체/월간/주간/일간)", description = "조건에 따라 식단 기록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<DietDto>> getDietLogs(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "일간 조회용 (YYYY-MM-DD)")
            @RequestParam(required = false) String date,

            @Parameter(description = "주간/기간 조회 시작일 (YYYY-MM-DD)")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "주간/기간 조회 종료일 (YYYY-MM-DD)")
            @RequestParam(required = false) String endDate,

            @Parameter(description = "월간 조회용 (YYYY-MM)")
            @RequestParam(required = false) String month) {

        Long userId = jwtUtil.getUserId(token);

        // 1. 일간 조회 (date가 있을 때)
        if (date != null) {
            return ResponseEntity.ok(dietService.getDailyDiets(userId, date));
        }

        // 2. ★ [추가됨] 주간(기간) 조회 (시작일과 종료일이 둘 다 있을 때)
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(dietService.getWeeklyDiets(userId, startDate, endDate));
        }

        // 3. 월간 조회 (month가 있을 때)
        if (month != null) {
            return ResponseEntity.ok(dietService.getMonthlyDiets(userId, month));
        }

        // 4. 아무것도 없으면 전체 조회
        return ResponseEntity.ok(dietService.getAllDiets(userId));
    }

    // 4. 식단 상세 조회
    @Operation(summary = "식단 상세 조회", description = "식단 ID(logId)를 통해 상세 음식 목록과 영양 정보를 조회합니다.")
    @GetMapping("/{dietId}")
    public ResponseEntity<DietDto> getDietDetail(
            @PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.getDietDetail(dietId));
    }

    // 5. 식단 수정
    @Operation(summary = "식단 수정", description = "기존 식단의 음식 목록이나 메모를 수정합니다.")
    @PutMapping("/{dietId}")
    public ResponseEntity<?> updateDiet(
            @PathVariable Long dietId,
            @RequestBody DietDto dietDto) {
        dietDto.setDietId(dietId);
        dietService.updateDiet(dietDto);
        return ResponseEntity.ok(Map.of("success", true, "message", "식단이 수정되었습니다."));
    }

    // 6. 식단 삭제
    @Operation(summary = "식단 삭제", description = "잘못 기록된 식단을 삭제합니다.")
    @DeleteMapping("/{dietId}")
    public ResponseEntity<?> deleteDiet(
            @PathVariable Long dietId) {
        dietService.deleteDiet(dietId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // 7. 다음 식단 추천
    @Operation(summary = "AI 다음 끼니 추천", description = "오늘 섭취한 식단을 분석하여 부족한 영양소를 채울 수 있는 메뉴를 추천합니다.")
    @GetMapping("/recommend")
    public ResponseEntity<?> recommendNextMeal(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token);

        // 1. 오늘 먹은 식단 조회
        String today = LocalDate.now().toString();
        List<DietDto> todaysLogs = dietService.getDailyDiets(userId, today);

        // 2. 기록이 없으면 분석 불가
        if (todaysLogs.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "recommendedMenus", List.of("집밥", "한식 정식", "샌드위치"),
                    "reason", "오늘 아직 기록된 식사가 없어요! 균형 잡힌 한 끼로 시작해보세요."
            ));
        }

        // 3. 식단 리스트를 문자열로 요약
        StringBuilder dietSummary = new StringBuilder();

        for (DietDto log : todaysLogs) {
            dietSummary.append("- [")
                    .append(log.getMealType())     // 예: LUNCH
                    .append("] ");

            //  메뉴 이름
            // 만약 메모가 비어있으면 "식사"라고만 표시
            if (log.getMemo() != null && !log.getMemo().isEmpty()) {
                dietSummary.append(log.getMemo());
            } else {
                dietSummary.append("기록된 식사");
            }

            dietSummary.append(" (");

            // 2. 칼로리
            if (log.getTotalKcal() != null) {
                dietSummary.append(log.getTotalKcal());
            } else {
                dietSummary.append("0");
            }

            dietSummary.append(" kcal)\n");
        }

        //  Gemini호출
        String recommendationJson = geminiService.recommendMenu(dietSummary.toString());

        return ResponseEntity.ok(recommendationJson);
    }
}
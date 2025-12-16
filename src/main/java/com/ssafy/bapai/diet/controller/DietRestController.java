package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diet-logs")
@RequiredArgsConstructor
@Tag(name = "2. 식단(Diet) API", description = "식단 기록, 조회, 수정, 삭제 (AI 기능은 /api/ai 로 이동됨)")
public class DietRestController {

    private final DietService dietService;
    private final JwtUtil jwtUtil;

    // 1. 식단 기록 저장
    @Operation(summary = "식단 기록 저장", description = "사용자가 확정한 식단 정보를 DB에 저장합니다.")
    @PostMapping
    public ResponseEntity<?> createDiet(@RequestHeader("Authorization") String token,
                                        @RequestBody DietDto dietDto) {
        Long userId = jwtUtil.getUserId(token);
        dietDto.setUserId(userId);

        dietService.saveDiet(dietDto);

        return ResponseEntity.ok(Map.of(
                "message", "식단이 저장되었습니다.",
                "logId", dietDto.getDietId() != null ? dietDto.getDietId() : -1L,
                "score", 85 // (추후 영양소 점수 계산 로직 추가 가능)
        ));
    }

    // 2. 식단 목록 조회 (월/일/주간)
    @Operation(summary = "식단 목록 조회", description = "일간, 주간, 월간, 전체 조건에 따라 식단 기록을 조회합니다.")
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

        // 1. 일간 조회
        if (date != null) {
            return ResponseEntity.ok(dietService.getDailyDiets(userId, date));
        }

        // 2. 주간(기간) 조회
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(dietService.getWeeklyDiets(userId, startDate, endDate));
        }

        // 3. 월간 조회
        if (month != null) {
            return ResponseEntity.ok(dietService.getMonthlyDiets(userId, month));
        }

        // 4. 전체 조회
        return ResponseEntity.ok(dietService.getAllDiets(userId));
    }

    // 3. 스트릭 조회 (URL 충돌 방지를 위해 상세 조회보다 위에 배치)
    @Operation(summary = "스트릭 정보 조회", description = "사용자의 연속 기록(스트릭) 정보를 반환합니다.")
    @GetMapping("/streak")
    public ResponseEntity<?> getStreak(@RequestHeader("Authorization") String token) {
        // Long userId = jwtUtil.getUserId(token);
        // return ResponseEntity.ok(dietService.getStreak(userId));
        return ResponseEntity.ok("스트릭 정보 (구현 필요 시 연결)");
    }

    // 4. 식단 상세 조회
    @Operation(summary = "식단 상세 조회", description = "식단 ID(logId)를 통해 상세 내용을 조회합니다.")
    @GetMapping("/{dietId}")
    public ResponseEntity<DietDto> getDietDetail(@PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.getDietDetail(dietId));
    }

    // 5. 식단 수정
    @Operation(summary = "식단 수정", description = "기존 식단의 메뉴나 메모를 수정합니다.")
    @PutMapping("/{dietId}")
    public ResponseEntity<?> updateDiet(@PathVariable Long dietId,
                                        @RequestBody DietDto dietDto) {
        dietDto.setDietId(dietId);
        dietService.updateDiet(dietDto);
        return ResponseEntity.ok(Map.of("success", true, "message", "식단이 수정되었습니다."));
    }

    // 6. 식단 삭제
    @Operation(summary = "식단 삭제", description = "기록된 식단을 삭제합니다.")
    @DeleteMapping("/{dietId}")
    public ResponseEntity<?> deleteDiet(@PathVariable Long dietId) {
        dietService.deleteDiet(dietId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
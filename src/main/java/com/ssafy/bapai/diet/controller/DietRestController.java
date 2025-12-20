package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/diet-logs")
@RequiredArgsConstructor
@Tag(name = "2. 식단(Diet) API", description = "식단 기록, 조회, 수정, 삭제")
public class DietRestController {

    private final DietService dietService;
    private final JwtUtil jwtUtil;
    private final S3Service s3Service;

    // 1. 식단 기록 저장 (수정됨: 둘 중 하나만 있어도 가능)
    @Operation(summary = "식단 기록 저장", description = "이미지와 식단 정보를 받아 AI 분석 후 저장합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDiet(
            @RequestHeader("Authorization") String token,

            // 1. DTO는 @ModelAttribute (입력창 쪼개기용)
            @ModelAttribute DietDto dietDto,

            // 2. 파일은 @RequestPart 대신 @RequestParam 사용 (스웨거 호환성 UP)
            @Parameter(description = "음식 사진 파일")
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        // 1. 사용자 ID 추출 및 설정
        Long userId = jwtUtil.getUserId(token.substring(7));

        if (dietDto == null) {
            dietDto = new DietDto();
        }
        dietDto.setUserId(userId);

        // 2. 날짜/식사시간 기본값 설정 (아까 만든 로직 유지)
        if (dietDto.getEatDate() == null || dietDto.getEatDate().isEmpty()) {
            dietDto.setEatDate(java.time.LocalDate.now().toString());
        }
        if (dietDto.getMealType() == null || dietDto.getMealType().isEmpty()) {
            dietDto.setMealType("SNACK");
        }

        // 3. ★ 서비스의 통합 메서드 호출 (업로드 + AI + 저장)
        try {
            dietService.registerDiet(dietDto, file);

            return ResponseEntity.ok(Map.of(
                    "message", "식단이 AI 분석 후 저장되었습니다.",
                    "imgUrl", dietDto.getDietImg() != null ? dietDto.getDietImg() : "",
                    "aiAnalysis",
                    dietDto.getAiAnalysis() != null ? dietDto.getAiAnalysis() : "분석 없음"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("식단 저장 중 오류 발생: " + e.getMessage());
        }
    }

    // 2. 식단 목록 조회
    @Operation(summary = "식단 목록 조회", description = "일간, 주간, 월간, 전체 조건에 따라 식단 기록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<DietDto>> getDietLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String month) {

        // ★ 수정 3: 여기도 substring(7)을 해줘야 Bearer를 떼고 ID를 읽습니다.
        Long userId = jwtUtil.getUserId(token.substring(7));

        if (date != null) {
            return ResponseEntity.ok(dietService.getDailyDiets(userId, date));
        }
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(dietService.getWeeklyDiets(userId, startDate, endDate));
        }
        if (month != null) {
            return ResponseEntity.ok(dietService.getMonthlyDiets(userId, month));
        }
        return ResponseEntity.ok(dietService.getAllDiets(userId));
    }

    // 3. 스트릭 조회
    @Operation(summary = "스트릭 정보 조회", description = "사용자의 연속 기록(스트릭) 정보를 반환합니다.")
    @GetMapping("/streak")
    public ResponseEntity<?> getStreak(@RequestHeader("Authorization") String token) {
        // Long userId = jwtUtil.getUserId(token.substring(7));
        return ResponseEntity.ok("스트릭 정보 (구현 예정)");
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
    public ResponseEntity<?> updateDiet(@PathVariable Long dietId, @RequestBody DietDto dietDto) {
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
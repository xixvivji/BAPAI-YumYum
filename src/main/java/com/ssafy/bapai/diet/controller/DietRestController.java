package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.ai.service.AiService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.dto.StreakDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/diet-logs")
@RequiredArgsConstructor
@Tag(name = "2. 식단(Diet) API", description = "식단 기록, 조회(개인/커뮤니티), 수정, 삭제")
public class DietRestController {

    private final DietService dietService;
    private final JwtUtil jwtUtil;
    private final S3Service s3Service;
    private final AiService aiService;

    @Operation(summary = "1단계: 식단 이미지 분석", description = "이미지를 보내면 AI가 분석한 음식 리스트와 영양소를 반환합니다.")
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DietDto> analyzeDiet(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "음식 사진") @RequestParam("file") MultipartFile file,
            @Parameter(description = "힌트(음식명)") @RequestParam(value = "hint", required = false)
            String hint) {

        jwtUtil.getUserId(token.substring(7));
        return ResponseEntity.ok(dietService.analyzeDiet(file, hint));
    }

    @Operation(summary = "식단 등록 (파라미터 방식)", description = "프론트에서 보내는 개별 데이터를 하나씩 받아서 저장합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDiet(
            @RequestHeader("Authorization") String token,
            // 1. 파일 받기
            @RequestPart(value = "file", required = false) MultipartFile file,

            // 2. 프론트엔드가 보내는 낱개 데이터들 (@RequestParam으로 하나씩 받기)
            @RequestParam("date") String date,
            @RequestParam("time") String time,
            @RequestParam("mealType") String mealType,
            @RequestParam("foodName") String foodName,
            @RequestParam(value = "servings", defaultValue = "1") int servings,
            // servings -> amount
            @RequestParam(value = "calories", defaultValue = "0") double calories,
// calories -> kcal
            @RequestParam(value = "carbs", defaultValue = "0") double carbs,
            @RequestParam(value = "protein", defaultValue = "0") double protein,
            @RequestParam(value = "fat", defaultValue = "0") double fat
    ) {
        Long userId = jwtUtil.getUserId(token.substring(7));

        // 3. 받은 낱개 데이터들을 DietDto로 조립하기
        DietDto dietDto = new DietDto();
        dietDto.setUserId(userId);
        dietDto.setEatDate(date);

        // 식사 타입 한글/영어 변환
        if ("아침".equals(mealType)) {
            dietDto.setMealType("BREAKFAST");
        } else if ("점심".equals(mealType)) {
            dietDto.setMealType("LUNCH");
        } else if ("저녁".equals(mealType)) {
            dietDto.setMealType("DINNER");
        } else if ("간식".equals(mealType)) {
            dietDto.setMealType("SNACK");
        } else {
            dietDto.setMealType(mealType != null ? mealType : "SNACK");
        }

        // 4. 음식 상세 정보(DietDetailDto) 만들어서 리스트에 넣기
        DietDetailDto detail = new DietDetailDto();
        detail.setFoodName(foodName);
        detail.setAmount(servings); // servings를 amount에 넣음
        detail.setKcal(calories);   // calories를 kcal에 넣음
        detail.setCarbs(carbs);
        detail.setProtein(protein);
        detail.setFat(fat);

        dietDto.setFoodList(java.util.Collections.singletonList(detail));

        // 총합 영양소 세팅 (일단 1개니까 그대로 대입)
        dietDto.setTotalKcal(calories);
        dietDto.setTotalCarbs(carbs);
        dietDto.setTotalProtein(protein);
        dietDto.setTotalFat(fat);

        try {
            // 5. 파일 있으면 업로드
            if (file != null && !file.isEmpty()) {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl);
            }

            // 6. 저장
            dietService.saveDiet(dietDto);

            return ResponseEntity.ok(
                    Map.of("message", "식단이 등록되었습니다.", "dietId", dietDto.getDietId()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "에러 발생: " + e.getMessage()));
        }
    }

    @Operation(summary = "내 식단 기록 조회 (통합)", description = "date(하루), startDate~endDate(주간), month(월간) 조건에 따라 다른 구조를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<?> getMyDietLogs(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String month) {

        Long userId = jwtUtil.getUserId(token.substring(7));

        // 1. 일간 조회 (객체 반환)
        if (date != null) {
            return ResponseEntity.ok(dietService.getDailyDietLog(userId, date));
        }

        // 2. 주간 조회 (1~7 키값 Map 반환)
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(dietService.getPeriodDietLogs(userId, startDate, endDate));
        }

        // 3. 월간 조회 (1~31 키값 Map 반환)
        if (month != null) {
            java.time.YearMonth ym = java.time.YearMonth.parse(month);
            String start = ym.atDay(1).toString();
            String end = ym.atEndOfMonth().toString();
            return ResponseEntity.ok(dietService.getPeriodDietLogs(userId, start, end));
        }

        // 4. 파라미터 없으면 전체 리스트 반환 (기존 유지)
        return ResponseEntity.ok(dietService.getAllDiets(userId));
    }

    @Operation(summary = "스트릭(연속 기록) 조회", description = "현재 스트릭과 역대 최장 스트릭을 반환합니다.")
    @GetMapping("/streak")
    public ResponseEntity<StreakDto> getStreak(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token.substring(7));

        return ResponseEntity.ok(dietService.getDietStreak(userId));
    }

    @Operation(summary = "식단 커뮤니티 피드 조회", description = "모든 사용자의 식단을 최신순/좋아요순/댓글순으로 조회합니다.")
    @GetMapping("/feed")
    public ResponseEntity<PageResponse<DietDto>> getDietFeed(
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "한 페이지당 개수") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬: latest, likes, comments")
            @RequestParam(defaultValue = "latest") String sort) {

        return ResponseEntity.ok(dietService.getDietFeed(sort, size, page));
    }


    @Operation(summary = "식단 상세 조회")
    @GetMapping("/{dietId}")
    public ResponseEntity<DietDto> getDietDetail(@PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.getDietDetail(dietId));
    }

    @Operation(summary = "식단 수정")
    @PutMapping(value = "/{dietId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateDiet(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long dietId,
            @ModelAttribute DietDto dietDto,
            @RequestParam(value = "file", required = false) MultipartFile file) { // ★ file 로 통일

        Long userId = jwtUtil.getUserId(token.substring(7));
        dietDto.setDietId(dietId);
        dietDto.setUserId(userId);

        try {
            if (file != null && !file.isEmpty()) {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl);
            }
            dietService.updateDiet(dietDto);
            return ResponseEntity.ok(Map.of("message", "식단이 수정되었습니다.")); // ★ success 제거
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "이미지 업로드 실패"));
        }
    }

    @Operation(summary = "식단 삭제")
    @DeleteMapping("/{dietId}")
    public ResponseEntity<?> deleteDiet(@PathVariable Long dietId) {
        dietService.deleteDiet(dietId);
        return ResponseEntity.ok(Map.of("message", "식단이 삭제되었습니다.")); // ★ success 제거
    }


    @Operation(summary = "물 섭취량 조절 (+/-)", description = "type에 'plus' 또는 'minus'를 보내 섭취량을 1씩 조절합니다.")
    @PostMapping("/water/count")
    public ResponseEntity<?> changeWaterCount(
            @RequestHeader("Authorization") String token,
            @RequestParam String date,
            @RequestParam String type // "plus" or "minus"
    ) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        int delta = "plus".equals(type) ? 1 : -1;

        dietService.changeWaterCount(userId, date, delta);
        return ResponseEntity.ok(Map.of("message", "물 섭취량이 변경되었습니다."));
    }

    /**
     * 2. 물 목표량 조절 (Plus / Minus)
     * 요청: POST /api/diet-logs/water/goal?date=2025-12-21&type=plus (또는 minus)
     */
    @Operation(summary = "물 목표량 조절 (+/-)", description = "type에 'plus' 또는 'minus'를 보내 목표량을 1씩 조절합니다.")
    @PostMapping("/water/goal")
    public ResponseEntity<?> changeWaterGoal(
            @RequestHeader("Authorization") String token,
            @RequestParam String date,
            @RequestParam String type // "plus" or "minus"
    ) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        int delta = "plus".equals(type) ? 1 : -1;

        dietService.changeWaterGoal(userId, date, delta);
        return ResponseEntity.ok(Map.of("message", "물 목표량이 변경되었습니다."));
    }
}
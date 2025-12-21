package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.ai.service.AiService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
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

    @Operation(summary = "2단계: 식단 최종 등록", description = "사용자가 검토/수정한 JSON 데이터를 DB에 저장합니다.")
    @PostMapping
    public ResponseEntity<?> createDiet(
            @RequestHeader("Authorization") String token,
            @RequestBody DietDto dietDto) {

        Long userId = jwtUtil.getUserId(token.substring(7));
        dietDto.setUserId(userId);
        if (dietDto.getEatDate() == null) {
            dietDto.setEatDate(java.time.LocalDate.now().toString());
        }
        if (dietDto.getMealType() == null) {
            dietDto.setMealType("SNACK");
        }

        try {
            dietService.saveDiet(dietDto);
            return ResponseEntity.ok(
                    Map.of("message", "식단이 등록되었습니다.", "dietId", dietDto.getDietId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "저장 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "내 식단 기록 조회", description = "날짜/기간 조건으로 내 기록을 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<List<DietDto>> getMyDietLogs(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String month) {

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

    @Operation(summary = "식단 커뮤니티 피드 조회", description = "모든 사용자의 식단을 최신순/좋아요순/댓글순으로 조회합니다.")
    @GetMapping("/feed")
    public ResponseEntity<PageResponse<DietDto>> getDietFeed(
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "한 페이지당 개수") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬: latest, likes, comments")
            @RequestParam(defaultValue = "latest") String sort) {

        return ResponseEntity.ok(dietService.getDietFeed(sort, size, page));
    }


    @Operation(summary = "내 스트릭(연속 기록일) 조회")
    @GetMapping("/streak")
    public ResponseEntity<?> getStreak(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        int streak = dietService.getDietStreak(userId);
        return ResponseEntity.ok(Map.of("streak", streak));
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
}
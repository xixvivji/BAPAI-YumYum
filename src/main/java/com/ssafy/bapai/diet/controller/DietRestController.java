package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.HashMap;
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

    // 1. 식단 기록 저장
    @Operation(summary = "식단 기록 저장", description = "이미지와 식단 정보를 받아 AI 분석 후 저장합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDiet(
            @RequestHeader("Authorization") String token,
            @ModelAttribute DietDto dietDto,
            @Parameter(description = "음식 사진 파일")
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        if (dietDto == null) {
            dietDto = new DietDto();
        }
        dietDto.setUserId(userId);

        if (dietDto.getEatDate() == null || dietDto.getEatDate().isEmpty()) {
            dietDto.setEatDate(java.time.LocalDate.now().toString());
        }
        if (dietDto.getMealType() == null || dietDto.getMealType().isEmpty()) {
            dietDto.setMealType("SNACK");
        }

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

    // 2. 내 식단 기록 조회 (캘린더/로그용)
    @Operation(summary = "내 식단 기록 조회 (캘린더)", description = "날짜(date), 기간(start~end), 월(month) 조건으로 내 기록을 조회합니다.")
    @GetMapping("/me") // URL 충돌 방지를 위해 '/me' 경로 추가 추천 (또는 파라미터로 구분)
    public ResponseEntity<List<DietDto>> getMyDietLogs(
            @RequestHeader("Authorization") String token,
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

    // ★ [추가됨] 3. 커뮤니티 피드 조회 (전체 공개 식단, 페이징+정렬)
    @Operation(summary = "식단 커뮤니티 피드 조회", description = "모든 사용자의 식단을 최신순/좋아요순/댓글순으로 조회합니다.")
    @GetMapping("/feed")
    public ResponseEntity<Map<String, Object>> getDietFeed(
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "한 페이지당 개수") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬: latest(최신), likes(좋아요), comments(댓글)")
            @RequestParam(defaultValue = "latest") String sort
    ) {
        int offset = (page - 1) * size;

        // 서비스에 getDietList 메서드가 필요합니다 (CommentService 패턴 참고)
        List<DietDto> list = dietService.getDietList(sort, size, offset);
        int totalCount = dietService.getDietCount();

        Map<String, Object> response = new HashMap<>();
        response.put("diets", list);
        response.put("totalCount", totalCount);
        response.put("currentPage", page);
        response.put("hasNext", (page * size) < totalCount);

        return ResponseEntity.ok(response);
    }

    // 4. 식단 상세 조회
    @Operation(summary = "식단 상세 조회", description = "식단 ID로 상세 내용을 조회합니다.")
    @GetMapping("/{dietId}")
    public ResponseEntity<DietDto> getDietDetail(@PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.getDietDetail(dietId));
    }

    // ★ [수정됨] 5. 식단 수정 (이미지 교체 가능)
    @Operation(summary = "식단 수정", description = "내용 및 이미지를 수정합니다.")
    @PutMapping(value = "/{dietId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateDiet(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @PathVariable Long dietId,
            @ModelAttribute DietDto dietDto, // JSON Body 대신 폼 데이터 사용
            @Parameter(description = "수정할 이미지 파일 (선택)")
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        Long userId = jwtUtil.getUserId(token.substring(7));
        dietDto.setDietId(dietId);
        dietDto.setUserId(userId);

        try {
            // 이미지 파일이 들어왔으면 S3에 업로드하고 URL 갱신
            if (file != null && !file.isEmpty()) {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl);
            }

            dietService.updateDiet(dietDto);
            return ResponseEntity.ok(Map.of("success", true, "message", "식단이 수정되었습니다."));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("이미지 업로드 실패");
        }
    }

    // 6. 식단 삭제
    @Operation(summary = "식단 삭제", description = "기록된 식단을 삭제합니다.")
    @DeleteMapping("/{dietId}")
    public ResponseEntity<?> deleteDiet(@PathVariable Long dietId) {
        dietService.deleteDiet(dietId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
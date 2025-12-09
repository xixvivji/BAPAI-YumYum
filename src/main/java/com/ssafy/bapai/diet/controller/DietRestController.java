package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "2. 식단(Diet) API", description = "식단 기록, 조회, AI 분석, 추천 등 ")
public class DietRestController {

    private final DietService dietService;
    private final JwtUtil jwtUtil;

//    // 1. 식단 이미지 AI 분석
//    @Operation(summary = "식단 이미지 AI 분석", description = "식단 사진을 업로드하면 AI가 음식 종류와 칼로리를 분석해서 반환합니다.")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "분석 성공", content = @Content(mediaType = "application/json", schema = @Schema(example = "{ \"predictedFoods\": [ {\"name\": \"김치찌개\", \"probability\": 98.5, \"foodCode\": \"D0001\"} ] }")))
//    })
//    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<?> analyzeDiet(
//            @Parameter(description = "업로드할 식단 이미지 파일 (.jpg, .png)", required = true)
//            @RequestPart("file") MultipartFile file) {
//
//       AI 모델 연동
//        return ResponseEntity.ok(Map.of(
//                "predictedFoods",
//                List.of(Map.of("name", "김치찌개", "probability", 98.5, "foodCode", "D0001"))
//        ));
//    }

    // 2. 식단 기록 저장 (최종)
    @Operation(summary = "식단 기록 저장 (최종)", description = "AI 분석 후 사용자가 확정한 식단 정보를 DB에 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 성공", content = @Content(mediaType = "application/json", schema = @Schema(example = "{ \"logId\": 1, \"score\": 85, \"message\": \"저장되었습니다.\" }")))
    })
    @PostMapping
    public ResponseEntity<?> createDiet(@RequestHeader("Authorization") String token,
                                        @RequestBody DietDto dietDto) {
        Long userId = jwtUtil.getUserId(token);
        dietDto.setUserId(userId);
        dietService.saveDiet(dietDto);
        return ResponseEntity.ok(Map.of(
                "message", "식단이 저장되었습니다.",
                "logId", dietDto.getDietId(),
                "score", 85 // 임시 점수
        ));
    }

    // 3. 식단 목록 조회 (월/일)
    @Operation(summary = "식단 목록 조회", description = "특정 날짜(일간) 또는 특정 월(월간)의 식단 기록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<DietDto>> getDietLogs(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "조회할 날짜 (YYYY-MM-DD)", example = "2025-11-21")
            @RequestParam(required = false) String date,
            @Parameter(description = "조회할 월 (YYYY-MM)", example = "2025-11")
            @RequestParam(required = false) String month) {

        Long userId = jwtUtil.getUserId(token);

        return ResponseEntity.ok(
                dietService.getDailyDiets(userId, date != null ? date : "2025-12-07"));
    }

    // 4. 식단 상세 조회
    @Operation(summary = "식단 상세 조회", description = "식단 ID(logId)를 통해 상세 음식 목록과 영양 정보를 조회합니다.")
    @GetMapping("/{dietId}")
    public ResponseEntity<DietDto> getDietDetail(
            @Parameter(description = "식단 ID (logId)", required = true, example = "1")
            @PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.getDietDetail(dietId));
    }

    // 5. 식단 수정
    @Operation(summary = "식단 수정", description = "기존 식단의 음식 목록이나 메모를 수정합니다.")
    @PutMapping("/{dietId}")
    public ResponseEntity<?> updateDiet(
            @Parameter(description = "식단 ID (logId)", required = true)
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
            @Parameter(description = "삭제할 식단 ID", required = true)
            @PathVariable Long dietId) {
        dietService.deleteDiet(dietId);
        return ResponseEntity.ok(Map.of("success", true));
    }

//    // 7. AI 다음 끼니 추천
//    @Operation(summary = "AI 다음 끼니 추천", description = "오늘 섭취한 영양 밸런스를 분석하여 다음 끼니 메뉴를 추천합니다.")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "추천 성공", content = @Content(mediaType = "application/json", schema = @Schema(example = "{ \"recommendedMenus\": [\"바나나\", \"닭가슴살\"], \"reason\": \"나트륨 과다\" }")))
//    })
//    @GetMapping("/recommend")
//    public ResponseEntity<?> recommendNextMeal(@RequestHeader("Authorization") String token) {
//        Service 구현 필요
//        return ResponseEntity.ok(Map.of(
//                "recommendedMenus", List.of("바나나", "닭가슴살 샐러드"),
//                "reason", "아침에 나트륨을 많이 드셨어요. 칼륨 배출이 필요합니다."
//        ));
//    }
}
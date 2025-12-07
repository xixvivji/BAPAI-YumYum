package com.ssafy.bapai.diet.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.service.DietService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diets")
@RequiredArgsConstructor
public class DietRestController {

    private final DietService dietService;
    private final JwtUtil jwtUtil;

    // 1. 식단 등록
    @Operation(summary = "식단 등록", description = "날짜, 끼니, 음식 목록을 저장합니다.")
    @PostMapping
    public ResponseEntity<?> createDiet(@RequestHeader("Authorization") String token,
                                        @RequestBody DietDto dietDto) {
        Long userId = jwtUtil.getUserId(token);
        dietDto.setUserId(userId);
        dietService.saveDiet(dietDto);
        return ResponseEntity.ok("식단이 성공적으로 등록되었습니다.");
    }

    // 2. 날짜별 조회
    @Operation(summary = "날짜별 식단 조회", description = "특정 날짜(yyyy-MM-dd)의 식단 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<DietDto>> getDailyDiets(@RequestHeader("Authorization") String token,
                                                       @RequestParam String date) {
        Long userId = jwtUtil.getUserId(token);
        return ResponseEntity.ok(dietService.getDailyDiets(userId, date));
    }

    // 3. 식단 삭제
    @Operation(summary = "식단 삭제", description = "식단 ID로 삭제합니다.")
    @DeleteMapping("/{dietId}")
    public ResponseEntity<?> deleteDiet(@PathVariable Long dietId) {
        dietService.deleteDiet(dietId);
        return ResponseEntity.ok("식단이 삭제되었습니다.");
    }
}
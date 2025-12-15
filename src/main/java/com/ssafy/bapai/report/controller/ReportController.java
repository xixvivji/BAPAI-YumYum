package com.ssafy.bapai.report.controller;

import com.ssafy.bapai.common.util.JwtUtil;
import com.ssafy.bapai.report.dto.GapReportDto;
import com.ssafy.bapai.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "7. Report", description = "AI 식단 분석 및 비교 리포트")
public class ReportController {

    private final ReportService reportService;
    private final JwtUtil jwtUtil;

    // URL path: /gap/{teamId} -> /gap/{groupId}
    @GetMapping("/gap/{groupId}")
    @Operation(summary = "비교 분석 리포트 조회",
            description = "나의 식단 vs 목표 vs 랭커(상위권)를 비교 분석합니다. type='WEEKLY' or 'MONTHLY'")
    public ResponseEntity<GapReportDto> getGapReport(
            @PathVariable Long groupId,
            @Parameter(description = "조회 기간 (WEEKLY, MONTHLY)", example = "WEEKLY")
            @RequestParam(defaultValue = "WEEKLY") String type,
            @RequestHeader("Authorization") String token) {

        Long userId = jwtUtil.getUserId(token.substring(7));

        // teamId -> groupId
        return ResponseEntity.ok(reportService.analyzeGap(userId, groupId, type));
    }
}
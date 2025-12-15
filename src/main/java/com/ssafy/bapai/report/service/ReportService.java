package com.ssafy.bapai.report.service;

import com.ssafy.bapai.member.dao.MemberGoalDao;
import com.ssafy.bapai.member.dto.MemberGoalDto;
import com.ssafy.bapai.report.dao.ReportDao;
import com.ssafy.bapai.report.dto.GapReportDto;
import com.ssafy.bapai.report.dto.ReportLogDto;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDao reportDao;
    private final MemberGoalDao memberGoalDao;

    // 분석 + 로그 저장까지 한번에
    @Transactional // 저장 로직이 들어가므로 트랜잭션 추가
    public GapReportDto analyzeGap(Long userId, Long groupId, String periodType) {
        GapReportDto dto = new GapReportDto();

        // 1. 기간 계산
        LocalDate end = LocalDate.now();
        LocalDate start;
        if ("MONTHLY".equalsIgnoreCase(periodType)) {
            start = end.minusDays(30);
        } else {
            start = end.minusDays(7);
        }

        // 2. 목표 조회
        MemberGoalDto goal = memberGoalDao.selectGoalByUserId(userId);
        if (goal != null) {
            dto.setGoalCalories(goal.getRecCalories());
            dto.setGoalCarbs(goal.getRecCarbs());
            dto.setGoalProtein(goal.getRecProtein());
            dto.setGoalFat(goal.getRecFat());
        }

        // 3. 통계 조회
        Map<String, Double> myStats =
                reportDao.selectMyStatsByPeriod(userId, start.toString(), end.toString());
        Map<String, Double> rankerStats =
                reportDao.selectRankerStatsByPeriod(groupId, start.toString(), end.toString());

        // 4. DTO 매핑
        dto.setMyAvgScore(myStats.get("avgScore"));
        dto.setMyAvgCalories(myStats.get("avgCalories"));
        dto.setMyAvgCarbs(myStats.get("avgCarbs"));
        dto.setMyAvgProtein(myStats.get("avgProtein"));
        dto.setMyAvgFat(myStats.get("avgFat"));

        dto.setRankerAvgScore(rankerStats.get("avgScore"));
        dto.setRankerAvgCarbs(rankerStats.get("avgCarbs"));
        dto.setRankerAvgProtein(rankerStats.get("avgProtein"));
        dto.setRankerAvgFat(rankerStats.get("avgFat"));

        // 5. AI 메시지 생성
        String aiMessage = generateAiMessage(dto);
        dto.setAnalysisMessage(aiMessage);

        //  6. 분석 결과 DB에 저장 (히스토리용)
        saveReportLog(userId, periodType, start, end, dto.getMyAvgScore(), aiMessage);

        return dto;
    }

    // 내부 저장 메서드
    private void saveReportLog(Long userId, String type, LocalDate start, LocalDate end,
                               double score, String msg) {
        ReportLogDto logDto = new ReportLogDto();
        logDto.setUserId(userId);
        logDto.setReportType(type); // WEEKLY or MONTHLY
        logDto.setStartDate(start);
        logDto.setEndDate(end);
        logDto.setScoreAverage(score);
        logDto.setAiMessage(msg);

        reportDao.insertReportLog(logDto);
    }

    private String generateAiMessage(GapReportDto dto) {
        // (생략: 기존과 동일)
        StringBuilder sb = new StringBuilder();
        if (dto.getGoalCalories() > 0) {
            if (dto.getMyAvgCalories() > dto.getGoalCalories() * 1.15) {
                sb.append("⚠️ 과식 주의! ");
            } else if (dto.getMyAvgCalories() < dto.getGoalCalories() * 0.8) {
                sb.append("⚠️ 섭취 부족! ");
            } else {
                sb.append("✅ 목표 달성! ");
            }
        }
        return sb.toString();
    }
}
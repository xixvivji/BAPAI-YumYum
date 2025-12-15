package com.ssafy.bapai.report.service;

import com.ssafy.bapai.member.dao.MemberGoalDao;
import com.ssafy.bapai.member.dto.MemberGoalDto;
import com.ssafy.bapai.report.dao.ReportDao;
import com.ssafy.bapai.report.dto.GapReportDto;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDao reportDao;
    private final MemberGoalDao memberGoalDao;

    // teamId -> groupId
    public GapReportDto analyzeGap(Long userId, Long groupId, String periodType) {
        GapReportDto dto = new GapReportDto();

        // 1. 기간 계산
        LocalDate end = LocalDate.now();
        LocalDate start;
        if ("MONTHLY".equalsIgnoreCase(periodType)) {
            start = end.minusDays(30);
        } else {
            start = end.minusDays(7); // WEEKLY
        }

        // 2. 목표 조회
        MemberGoalDto goal = memberGoalDao.selectGoalByUserId(userId);
        if (goal != null) {
            dto.setGoalCalories(goal.getRecCalories());
            dto.setGoalCarbs(goal.getRecCarbs());
            dto.setGoalProtein(goal.getRecProtein());
            dto.setGoalFat(goal.getRecFat());
        }

        // 3. 통계 조회 (groupId 전달)
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

        // 5. AI 분석
        dto.setAnalysisMessage(generateAiMessage(dto));

        return dto;
    }

    private String generateAiMessage(GapReportDto dto) {
        StringBuilder sb = new StringBuilder();

        // 목표 비교
        if (dto.getGoalCalories() > 0) {
            if (dto.getMyAvgCalories() > dto.getGoalCalories() * 1.15) {
                sb.append("⚠️ 목표보다 과식하고 계십니다! ");
            } else if (dto.getMyAvgCalories() < dto.getGoalCalories() * 0.8) {
                sb.append("⚠️ 너무 적게 드셨네요. ");
            } else {
                sb.append("✅ 목표 칼로리를 잘 지키고 계십니다! ");
            }
        }

        // 랭커 비교
        double scoreGap = dto.getRankerAvgScore() - dto.getMyAvgScore();
        if (scoreGap > 10) {
            sb.append("\n🏆 상위권 멤버들은 회원님보다 평균 ").append((int) scoreGap).append("점 높습니다. ");
            if (dto.getRankerAvgProtein() > dto.getMyAvgProtein() + 15) {
                sb.append("단백질 섭취량이 부족해보이네요.");
            } else {
                sb.append("식단 구성을 좀 더 신경써보세요.");
            }
        } else {
            sb.append("\n🔥 대단해요! 팀 내 상위권 수준입니다.");
        }
        return sb.toString();
    }
}
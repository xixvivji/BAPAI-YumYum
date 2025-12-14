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
    private final MemberGoalDao memberGoalDao; // 목표 비교용

    // 기간별 갭 분석 (WEEKLY, MONTHLY)
    public GapReportDto analyzeGap(Long userId, Long teamId, String periodType) {
        GapReportDto dto = new GapReportDto();

        // 1. 기간 계산 (오늘 ~ N일 전)
        LocalDate end = LocalDate.now();
        LocalDate start;
        if ("MONTHLY".equalsIgnoreCase(periodType)) {
            start = end.minusDays(30);
        } else {
            start = end.minusDays(7); // 기본값: 주간
        }

        // 2. 내 목표 가져오기 (설정 안했으면 0으로 나옴)
        MemberGoalDto goal = memberGoalDao.selectGoalByUserId(userId);
        if (goal != null) {
            dto.setGoalCalories(goal.getRecCalories());
            dto.setGoalCarbs(goal.getRecCarbs());
            dto.setGoalProtein(goal.getRecProtein());
            dto.setGoalFat(goal.getRecFat());
        }

        // 3. 통계 데이터 조회 (DAO 호출)
        // 날짜를 String으로 변환해서 전달 ("2024-05-01")
        Map<String, Double> myStats =
                reportDao.selectMyStatsByPeriod(userId, start.toString(), end.toString());
        Map<String, Double> rankerStats =
                reportDao.selectRankerStatsByPeriod(teamId, start.toString(), end.toString());

        // 4. DTO에 매핑
        dto.setMyAvgScore(myStats.get("avgScore"));
        dto.setMyAvgCalories(myStats.get("avgCalories"));
        dto.setMyAvgCarbs(myStats.get("avgCarbs"));
        dto.setMyAvgProtein(myStats.get("avgProtein"));
        dto.setMyAvgFat(myStats.get("avgFat"));

        dto.setRankerAvgScore(rankerStats.get("avgScore"));
        dto.setRankerAvgCarbs(rankerStats.get("avgCarbs"));
        dto.setRankerAvgProtein(rankerStats.get("avgProtein"));
        dto.setRankerAvgFat(rankerStats.get("avgFat"));

        // 5. [AI 로직] 비교 분석 메시지 생성
        dto.setAnalysisMessage(generateAiMessage(dto));

        return dto;
    }

    // AI가 분석해주는 척하는 메서드
    private String generateAiMessage(GapReportDto dto) {
        StringBuilder sb = new StringBuilder();

        // (1) 목표 달성 여부 체크
        if (dto.getGoalCalories() > 0) {
            if (dto.getMyAvgCalories() > dto.getGoalCalories() * 1.15) {
                sb.append("⚠️ 목표 칼로리보다 과식하고 계십니다! 조금 줄여보세요. ");
            } else if (dto.getMyAvgCalories() < dto.getGoalCalories() * 0.8) {
                sb.append("⚠️ 너무 적게 드셨네요. 에너지가 부족할 수 있어요. ");
            } else {
                sb.append("✅ 목표 칼로리를 완벽하게 지키고 계십니다! ");
            }
        }

        // (2) 랭커와 비교
        double scoreGap = dto.getRankerAvgScore() - dto.getMyAvgScore();
        if (scoreGap > 10) {
            sb.append("\n🏆 상위권 멤버들은 회원님보다 평균 ").append((int) scoreGap).append("점 더 높습니다. ");
            // 단백질 비교
            if (dto.getRankerAvgProtein() > dto.getMyAvgProtein() + 15) {
                sb.append("비결은 '단백질' 섭취량이네요! 닭가슴살을 추가해보세요.");
            } else {
                sb.append("식단 구성을 조금 더 다채롭게 바꿔보세요.");
            }
        } else {
            sb.append("\n🔥 대단해요! 팀 내 상위권 수준의 식단 관리를 하고 계십니다.");
        }

        return sb.toString();
    }
}
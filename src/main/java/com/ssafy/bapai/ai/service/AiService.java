package com.ssafy.bapai.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.ai.dao.ReportDao;
import com.ssafy.bapai.ai.dto.AiReportResponse;
import com.ssafy.bapai.ai.dto.ReportLogDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DietDto;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ObjectMapper objectMapper;
    private final ReportDao reportDao;
    private final DietDao dietDao;
    private final GeminiService geminiService;

    // 1. 이미지 분석
    public String analyzeImage(MultipartFile file) {
        return geminiService.analyzeFoodImage(file);
    }

    // 2. 다음 끼니 추천
    public String recommendNextMeal(Long userId) {
        String today = LocalDate.now().toString();
        List<DietDto> logs = dietDao.selectDailyDiets(userId, today);

        String prompt;
        if (logs.isEmpty()) {
            prompt = "오늘 기록된 식사가 없어. 가볍고 건강한 메뉴 3가지를 추천해줘.";
        } else {
            // 음식 이름과 칼로리 정보만 문자열로 요약해서 AI에게 전달
            StringBuilder sb = new StringBuilder();
            for (DietDto log : logs) {
                // 메뉴명이 없으면 메모나 타입 사용
                String menuName =
                        (log.getMemo() != null && !log.getMemo().isEmpty()) ? log.getMemo() :
                                log.getMealType();
                sb.append(menuName).append("(").append(log.getTotalKcal()).append("kcal), ");
            }
            prompt = "오늘 먹은 음식: " + sb.toString() + ". 부족한 영양소를 추측해서 다음 끼니 메뉴 3가지를 추천해줘.";
        }

        // ✅ [수정] recommendChallengesByAI -> chatWithText
        return geminiService.chatWithText(prompt);
    }

    // 3. 일간 리포트
    @Transactional
    public AiReportResponse getDailyReport(Long userId, String date) {
        if (date == null) {
            date = LocalDate.now().toString();
        }

        // (1) DB 조회
        List<DietDto> dailyLogs = dietDao.selectDailyDiets(userId, date);

        // (2) 캐싱 확인
        ReportLogDto cachedLog = reportDao.selectExistingReport(userId, "DAILY", date, date);

        if (cachedLog != null) {
            return AiReportResponse.builder()
                    .type("DAILY").dateRange(date)
                    .dailyMeals(dailyLogs)
                    .aiAnalysis(cachedLog.getAiMessage())
                    .build();
        }

        // (3) AI 분석 요청
        String aiMessage;
        if (dailyLogs.isEmpty()) {
            aiMessage = "기록된 식단이 없습니다. 오늘의 식사를 기록해보세요!";
        } else {
            StringBuilder sb = new StringBuilder("오늘 식단 리스트:\n");
            double totalKcal = 0;

            for (DietDto d : dailyLogs) {
                String menuName = (d.getMemo() != null) ? d.getMemo() : d.getMealType();
                double kcal = (d.getTotalKcal() != null) ? d.getTotalKcal() : 0.0;

                sb.append("- ").append(menuName).append(" (").append(kcal).append("kcal)\n");
                totalKcal += kcal;
            }

            String prompt = sb.toString() +
                    "\n총 섭취 칼로리: " + totalKcal + "kcal.\n" +
                    "위 메뉴 구성을 보고 영양 균형이 맞는지, 부족한 영양소는 무엇인지 3줄로 요약해서 조언해줘.";

            // ✅ [수정] recommendChallengesByAI -> chatWithText
            aiMessage = geminiService.chatWithText(prompt);
        }

        // (4) 저장
        if (!dailyLogs.isEmpty()) {
            ReportLogDto newLog = ReportLogDto.builder()
                    .userId(userId).reportType("DAILY")
                    .startDate(date).endDate(date)
                    .scoreAverage(0.0)
                    .aiMessage(aiMessage)
                    .build();
            reportDao.insertReportLog(newLog);
        }

        return AiReportResponse.builder()
                .type("DAILY").dateRange(date)
                .dailyMeals(dailyLogs)
                .aiAnalysis(aiMessage)
                .build();
    }

    // 4. 주간/월간 리포트
    @Transactional
    public AiReportResponse getPeriodReport(Long userId, String type) {
        LocalDate end = LocalDate.now();
        LocalDate start = type.equals("WEEKLY") ? end.minusWeeks(1) : end.minusMonths(1);
        String sDate = start.toString();
        String eDate = end.toString();

        ReportLogDto cachedLog = reportDao.selectExistingReport(userId, type, sDate, eDate);
        if (cachedLog != null) {
            return AiReportResponse.builder()
                    .type(type).dateRange(sDate + " ~ " + eDate)
                    .averageScore(cachedLog.getScoreAverage())
                    .aiAnalysis(cachedLog.getAiMessage())
                    .build();
        }

        List<DietDto> logs = dietDao.selectWeeklyDiets(userId, sDate, eDate);

        List<Integer> scores = logs.stream()
                .map(DietDto::getScore)
                .filter(s -> s != null && s > 0)
                .collect(Collectors.toList());

        double avgScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        avgScore = Math.round(avgScore * 10) / 10.0;

        String periodName = type.equals("WEEKLY") ? "지난 1주" : "지난 1달";
        String prompt = String.format("%s 동안의 평균 식단 점수는 %.1f점이야. 점수 리스트: %s.\n" +
                        "점수 변화 추세를 보고 칭찬이나 개선점을 따뜻하게 조언해줘.",
                periodName, avgScore, scores.toString());

        // ✅ [수정] recommendChallengesByAI -> chatWithText
        String aiMessage = geminiService.chatWithText(prompt);

        // 저장
        ReportLogDto newLog = ReportLogDto.builder()
                .userId(userId).reportType(type)
                .startDate(sDate).endDate(eDate)
                .scoreAverage(avgScore)
                .aiMessage(aiMessage)
                .build();
        reportDao.insertReportLog(newLog);

        return AiReportResponse.builder()
                .type(type).dateRange(sDate + " ~ " + eDate)
                .averageScore(avgScore)
                .scoreTrend(scores)
                .aiAnalysis(aiMessage)
                .build();
    }

    public List<ChallengePresetDto> recommendGroupChallenges(List<String> keywords) {
        // 1. 키워드 정리
        String keywordStr = (keywords == null || keywords.isEmpty()) ? "건강, 운동, 식습관" :
                String.join(", ", keywords);

        // 2. 프롬프트 작성
        String prompt = """
                사용자 그룹의 관심사: [%s]
                
                이 관심사에 맞는 그룹 챌린지 주제 3가지를 추천해줘.
                응답은 반드시 아래 JSON 배열 포맷으로만 출력해. (설명 금지)
                
                [
                    {
                        "presetId": null,
                        "title": "챌린지 제목 (이모지 포함)",
                        "content": "인증 방법 설명",
                        "goalType": "COUNT", 
                        "targetCount": 5,
                        "keyword": "메인키워드"
                    }
                ]
                """.formatted(keywordStr);

        // 3. AI 호출
        String jsonResult = geminiService.chatWithText(prompt);

        // 4. JSON -> 객체 변환
        try {
            ChallengePresetDto[] array =
                    objectMapper.readValue(jsonResult, ChallengePresetDto[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            log.error("AI 챌린지 추천 파싱 실패", e);
            return Collections.emptyList();
        }
    }
}
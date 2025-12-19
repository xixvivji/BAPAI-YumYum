package com.ssafy.bapai.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.ai.dao.ReportDao;
import com.ssafy.bapai.ai.dto.AiReportResponse;
import com.ssafy.bapai.ai.dto.GapReportDto;
import com.ssafy.bapai.ai.dto.ReportLogDto;
import com.ssafy.bapai.challenge.dto.ChallengePresetDto;
import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DietDto;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class AiService {

    private final ObjectMapper objectMapper;
    private final ReportDao reportDao;
    private final DietDao dietDao;
    private final ChatClient visionClient;
    private final ChatClient reportClient;

    // 생성자 주입
    public AiService(ObjectMapper objectMapper,
                     ReportDao reportDao,
                     DietDao dietDao,
                     @Qualifier("visionChatClient") ChatClient visionClient,
                     @Qualifier("reportChatClient") ChatClient reportClient) {
        this.objectMapper = objectMapper;
        this.reportDao = reportDao;
        this.dietDao = dietDao;
        this.visionClient = visionClient;
        this.reportClient = reportClient;
    }

    // 1. 이미지 분석 (압축 버전)
    public String analyzeImage(MultipartFile file) {
        try {
            // 압축 수행 (512px)
            byte[] compressedImage = compressImage(file);

            // 리소스 생성 (파일 이름 강제 지정 - 호환성 향상)
            Resource imageResource =
                    new org.springframework.core.io.ByteArrayResource(compressedImage) {
                        @Override
                        public String getFilename() {
                            return "image.jpg";
                        }
                    };

            String prompt = """
                    이 음식 사진을 분석해서 다음 JSON 형식으로만 답해줘. 마크다운(```json)이나 설명 없이 순수 JSON 문자열만 줘.
                    {
                        "menuName": "음식명(한글)",
                        "calories": 300,
                        "carbs": 50,
                        "protein": 20,
                        "fat": 10,
                        "score": 85
                    }
                    """;

            return visionClient.prompt()
                    .user(u -> u.text(prompt)
                            .media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("이미지 분석 실패: " + e.getMessage()); // 스택 트레이스 대신 메시지만 깔끔하게
            return "{}";
        }
    }

    //  512px 리사이징 & 압축 로직
    private byte[] compressImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IllegalArgumentException("이미지 파일이 아닙니다.");
        }

        // 기존 1024 -> 512로 변경
        int targetWidth = 512;
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 이미 작으면 그대로 쓰되, JPG 변환은 수행 (PNG 용량이 클 수 있으므로)
        if (originalWidth <= targetWidth) {
            targetWidth = originalWidth;
        }

        int targetHeight = (int) ((double) targetWidth / originalWidth * originalHeight);

        BufferedImage resizedImage =
                new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", outputStream);

        byte[] result = outputStream.toByteArray();

        //  디버깅
        System.out.println(
                "📸 [이미지 압축] " + (file.getSize() / 1024) + "KB -> " + (result.length / 1024) +
                        "KB (width: " + targetWidth + "px)");

        return result;
    }

    // 2. 다음 끼니 추천
    public String recommendNextMeal(Long userId) {
        String today = LocalDate.now().toString();
        List<DietDto> logs = dietDao.selectDailyDiets(userId, today);

        String prompt;
        if (logs.isEmpty()) {
            prompt = "오늘 기록된 식사가 없어. 가볍고 건강한 메뉴 3가지를 추천해줘.";
        } else {
            StringBuilder sb = new StringBuilder();
            for (DietDto log : logs) {
                String menuName =
                        (log.getMemo() != null && !log.getMemo().isEmpty()) ? log.getMemo() :
                                log.getMealType();
                sb.append(menuName).append("(").append(log.getTotalKcal()).append("kcal), ");
            }
            prompt = "오늘 먹은 음식: " + sb.toString() + ". 부족한 영양소를 추측해서 다음 끼니 메뉴 3가지를 추천해줘.";
        }

        return visionClient.prompt().user(prompt).call().content();
    }

    // 3. 일간 리포트
    @Transactional
    public AiReportResponse getDailyReport(Long userId, String date) {
        if (date == null) {
            date = LocalDate.now().toString();
        }

        List<DietDto> dailyLogs = dietDao.selectDailyDiets(userId, date);
        ReportLogDto cachedLog = reportDao.selectExistingReport(userId, "DAILY", date, date);

        if (cachedLog != null) {
            return AiReportResponse.builder()
                    .type("DAILY").dateRange(date).dailyMeals(dailyLogs)
                    .aiAnalysis(cachedLog.getAiMessage()).build();
        }

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
            String prompt = sb.toString() + "\n총 " + totalKcal + "kcal. 영양 균형 조언 3줄 요약해줘.";

            aiMessage = visionClient.prompt().user(prompt).call().content();
        }

        if (!dailyLogs.isEmpty()) {
            reportDao.insertReportLog(ReportLogDto.builder()
                    .userId(userId).reportType("DAILY").startDate(date).endDate(date)
                    .scoreAverage(0.0).aiMessage(aiMessage).build());
        }

        return AiReportResponse.builder()
                .type("DAILY").dateRange(date).dailyMeals(dailyLogs)
                .aiAnalysis(aiMessage).build();
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
                    .aiAnalysis(cachedLog.getAiMessage()).build();
        }

        List<DietDto> logs = dietDao.selectWeeklyDiets(userId, sDate, eDate);
        List<Integer> scores = logs.stream().map(DietDto::getScore)
                .filter(s -> s != null && s > 0).collect(Collectors.toList());
        double avgScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        avgScore = Math.round(avgScore * 10) / 10.0;

        String periodName = type.equals("WEEKLY") ? "지난 1주" : "지난 1달";
        String prompt = String.format("%s 동안 평균 %.1f점. 점수 리스트: %s. 추세 분석 및 심층 조언해줘.",
                periodName, avgScore, scores.toString());

        String aiMessage = reportClient.prompt().user(prompt).call().content();

        reportDao.insertReportLog(ReportLogDto.builder()
                .userId(userId).reportType(type).startDate(sDate).endDate(eDate)
                .scoreAverage(avgScore).aiMessage(aiMessage).build());

        return AiReportResponse.builder()
                .type(type).dateRange(sDate + " ~ " + eDate)
                .averageScore(avgScore).scoreTrend(scores).aiAnalysis(aiMessage).build();
    }

    // 5. 챌린지 추천
    public List<ChallengePresetDto> recommendGroupChallenges(List<String> keywords) {
        String keywordStr =
                (keywords == null || keywords.isEmpty()) ? "건강, 운동" : String.join(", ", keywords);
        String prompt = """
                주제: [%s]. 그룹 챌린지 주제 3가지를 추천해줘.
                응답은 오직 JSON 배열 포맷으로만:
                [{"title":"제목","content":"내용","goalType":"COUNT","targetCount":5,"keyword":"키워드"}]
                """.formatted(keywordStr);

        String jsonResult = visionClient.prompt().user(prompt).call().content();

        try {
            if (jsonResult.startsWith("```")) {
                jsonResult = jsonResult.replaceAll("```json", "").replaceAll("```", "");
            }
            ChallengePresetDto[] array =
                    objectMapper.readValue(jsonResult, ChallengePresetDto[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            log.error("챌린지 추천 파싱 실패", e);
            return Collections.emptyList();
        }
    }

    // 6. 비교 분석 Gap Analysis (핵심 기능 - 실전 복구 완료)
    @Transactional
    public GapReportDto getGapAnalysis(Long userId, Long groupId, String type) {
        LocalDate end = LocalDate.now();
        LocalDate start = "MONTHLY".equals(type) ? end.minusMonths(1) : end.minusWeeks(1);
        String sDate = start.toString();
        String eDate = end.toString();

        // (1) DB 통계 조회
        Map<String, Object> myStats = reportDao.selectMyStats(userId, sDate, eDate);
        Map<String, Object> rankerStats = reportDao.selectRankerStats(groupId, sDate, eDate);

        // (2) 안전한 값 추출 (DB 데이터가 없으면 0.0 처리)
        double myScore = getSafeDouble(myStats, "avgScore");
        double myKcal = getSafeDouble(myStats, "avgKcal");
        double rankerScore = getSafeDouble(rankerStats, "avgScore");
        double rankerKcal = getSafeDouble(rankerStats, "avgKcal");

        // 목표 칼로리는 임시 2000 (나중에 회원 목표 테이블과 연동 추천)
        double goalKcal = 2000.0;

        // (3) 캐싱 확인 & AI 분석
        ReportLogDto cached = reportDao.selectExistingReport(userId, "GAP_ANALYSIS", sDate, eDate);
        String aiMessage;

        if (cached != null) {
            aiMessage = cached.getAiMessage();
        } else {
            // 데이터가 아예 없을 경우를 대비한 멘트 처리
            if (myScore == 0 && myKcal == 0) {
                aiMessage = "아직 충분한 식단 기록이 없습니다. 식단을 꾸준히 기록하면 분석해드릴게요!";
            } else {
                String prompt = String.format(
                        "나(평균 %.1f점, %.0fkcal) vs 랭커(평균 %.1f점, %.0fkcal) vs 목표(%.0fkcal). " +
                                "내가 부족한 점과 잘한 점을 냉철하게 비교 분석하고 동기부여해줘. 3줄 요약.",
                        myScore, myKcal, rankerScore, rankerKcal, goalKcal
                );
                aiMessage = reportClient.prompt().user(prompt).call().content();

                // DB 저장
                reportDao.insertReportLog(ReportLogDto.builder()
                        .userId(userId).reportType("GAP_ANALYSIS").startDate(sDate).endDate(eDate)
                        .scoreAverage(myScore).aiMessage(aiMessage).build());
            }
        }

        return GapReportDto.builder()
                .myAvgScore(myScore).myTotalKcal(myKcal)
                .rankerAvgScore(rankerScore).rankerTotalKcal(rankerKcal)
                .goalKcal(goalKcal).aiAnalysis(aiMessage).build();
    }

    // ★ Null 방지용 헬퍼 메서드
    private double getSafeDouble(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return 0.0;
        }
        return ((Number) map.get(key)).doubleValue();
    }
}
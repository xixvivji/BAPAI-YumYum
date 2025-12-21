package com.ssafy.bapai.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
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
    private final ChatModel chatModel;

    public AiService(ObjectMapper objectMapper,
                     ReportDao reportDao,
                     DietDao dietDao,
                     ChatModel chatModel,
                     @Qualifier("visionChatClient") ChatClient visionClient,
                     @Qualifier("reportChatClient") ChatClient reportClient) {
        this.objectMapper = objectMapper;
        this.reportDao = reportDao;
        this.dietDao = dietDao;
        this.visionClient = visionClient;
        this.reportClient = reportClient;
        this.chatModel = chatModel;
    }

    /**
     * 1. 음식 분석 (가장 중요)
     * - 프롬프트 강화 + 응답 클리닝 + 예외 처리 적용
     */
    public String analyzeFood(MultipartFile file, String foodName) {
        // 에러 발생 시 반환할 안전한 기본값
        String fallbackJson = createDefaultJson();

        try {
            // ★ [안전장치 1] 힌트 유효성 검사 (숫자, 특수문자만 있거나 너무 짧으면 무시)
            String userHint = "";
            if (foodName != null && !foodName.isBlank()) {
                // 한글/영어가 하나도 없이 숫자나 특수문자만 있는지 정규식으로 검사
                boolean isGarbage =
                        foodName.matches("^[0-9\\s!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$");

                if (!isGarbage) {
                    // 유효한 힌트일 때만 프롬프트에 추가
                    userHint = " (사용자가 제공한 힌트: '" + foodName + "'. 단, 이 힌트가 사진과 명확히 다르다면 무시하세요.)";
                }
            }

            // ★ [안전장치 2] 프롬프트 강화: 사진 우선 원칙 명시
            String systemInstruction = """
                    당신은 전문 영양사입니다. 음식 사진을 분석하여 영양 정보를 추정하세요.
                    
                    [분석 규칙]
                    1. 사용자의 힌트가 있더라도, **사진 속 음식과 다르면 사진을 최우선으로 분석**하세요.
                    2. 음식 이름은 반드시 한국인이 흔히 쓰는 **한글 명칭**으로 작성하세요. (예: 'Kimchi Stew' -> '김치찌개')
                    3. 응답은 오직 아래 JSON 포맷으로만 작성하세요. 마크다운이나 잡담 금지.
                    
                    [JSON 응답 예시]
                    {
                        "foodName": "김치찌개",
                        "kcal": 450,
                        "carbs": 20.5,
                        "protein": 15.0,
                        "fat": 10.2,
                        "score": 85,
                        "aiAnalysis": "단백질이 풍부하지만 나트륨이 조금 많아 보입니다."
                    }
                    """;

            String aiResponseRaw;

            // 1. 이미지 분석 요청
            if (file != null && !file.isEmpty()) {
                byte[] compressedImage = compressImage(file);
                Resource imageResource = new ByteArrayResource(compressedImage);

                String promptText = "이 음식 사진을 분석해줘." + userHint + "\n" + systemInstruction;

                var userMessage = new UserMessage(
                        promptText,
                        List.of(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                );
                aiResponseRaw =
                        chatModel.call(new Prompt(userMessage)).getResult().getOutput().getText();
            }
            // 2. 텍스트 분석 요청 (이미지 없을 때)
            else {
                // 이미지가 없는데 힌트까지 이상하면("1234") -> 기본값 리턴이 나음
                if (userHint.isBlank()) {
                    return fallbackJson;
                }
                String promptText =
                        "음식 사진은 없어." + userHint + " 일반적인 1인분 기준으로 분석해줘.\n" + systemInstruction;
                aiResponseRaw =
                        chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
            }

            // 후처리 (JSON 클리닝 및 검증)
            String cleanJson = cleanJsonOutput(aiResponseRaw);
            objectMapper.readTree(cleanJson); // 파싱 테스트

            return cleanJson;

        } catch (Exception e) {
            log.error("AI 분석 실패: {}", e.getMessage());
            return fallbackJson;
        }
    }

    // --- 헬퍼 메서드: JSON 클리닝 ---
    private String cleanJsonOutput(String text) {
        if (text == null) {
            return "{}";
        }
        return text.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
    }

    // --- 헬퍼 메서드: 기본 JSON 생성 (에러 방지용) ---
    private String createDefaultJson() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("foodName", "분석 실패 (직접 입력해주세요)");
        root.put("kcal", 0);
        root.put("carbs", 0.0);
        root.put("protein", 0.0);
        root.put("fat", 0.0);
        root.put("score", 0);
        root.put("aiAnalysis", "죄송합니다. 음식 분석에 실패했습니다.");
        return root.toString();
    }

    // ---------------------------------------------------------
    // 아래는 기존 로직 유지 (이미지 압축, 리포트 등)
    // ---------------------------------------------------------

    private byte[] compressImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IllegalArgumentException("이미지 파일이 아닙니다.");
        }

        int targetWidth = 512; // 512px면 AI 인식에 충분함
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

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
        return outputStream.toByteArray();
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

        // ★ 여기도 방어 로직 적용 (마크다운 제거)
        jsonResult = cleanJsonOutput(jsonResult);

        try {
            ChallengePresetDto[] array =
                    objectMapper.readValue(jsonResult, ChallengePresetDto[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            log.error("챌린지 추천 파싱 실패", e);
            return Collections.emptyList();
        }
    }

    // 6. Gap Analysis
    @Transactional
    public GapReportDto getGapAnalysis(Long userId, Long groupId, String type) {
        LocalDate end = LocalDate.now();
        LocalDate start = "MONTHLY".equals(type) ? end.minusMonths(1) : end.minusWeeks(1);
        String sDate = start.toString();
        String eDate = end.toString();

        Map<String, Object> myStats = reportDao.selectMyStats(userId, sDate, eDate);
        Map<String, Object> rankerStats = reportDao.selectRankerStats(groupId, sDate, eDate);

        double myScore = getSafeDouble(myStats, "avgScore");
        double myKcal = getSafeDouble(myStats, "avgKcal");
        double rankerScore = getSafeDouble(rankerStats, "avgScore");
        double rankerKcal = getSafeDouble(rankerStats, "avgKcal");
        double goalKcal = 2000.0;

        ReportLogDto cached = reportDao.selectExistingReport(userId, "GAP_ANALYSIS", sDate, eDate);
        String aiMessage;

        if (cached != null) {
            aiMessage = cached.getAiMessage();
        } else {
            if (myScore == 0 && myKcal == 0) {
                aiMessage = "아직 충분한 식단 기록이 없습니다. 식단을 꾸준히 기록하면 분석해드릴게요!";
            } else {
                String prompt = String.format(
                        "나(평균 %.1f점, %.0fkcal) vs 랭커(평균 %.1f점, %.0fkcal) vs 목표(%.0fkcal). " +
                                "내가 부족한 점과 잘한 점을 냉철하게 비교 분석하고 동기부여해줘. 3줄 요약.",
                        myScore, myKcal, rankerScore, rankerKcal, goalKcal
                );
                aiMessage = reportClient.prompt().user(prompt).call().content();

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

    private double getSafeDouble(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return 0.0;
        }
        return ((Number) map.get(key)).doubleValue();
    }
}
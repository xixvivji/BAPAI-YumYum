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
import java.util.function.Supplier;
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

    private static final int MAX_PROMPT_TEXT_LEN = 800; // 프롬프트에 섞는 사용자 텍스트 상한
    private static final int MAX_LIST_ITEMS = 50;       // 리스트 상한 (토큰 폭발 방지)
    private static final long MAX_IMAGE_PIXELS = 4096L * 4096L; // 픽셀 상한(완화 목적)

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

    // -----------------------------
    // ✅ 공통 방어 유틸
    // -----------------------------
    private String sanitizeForPrompt(String input) {
        if (input == null) {
            return "";
        }
        String s = input.replaceAll("\\p{Cntrl}", " "); // 제어문자 제거
        s = s.trim();
        if (s.length() > MAX_PROMPT_TEXT_LEN) {
            s = s.substring(0, MAX_PROMPT_TEXT_LEN) + "...";
        }

        // 단순 인젝션 완화(완벽 X, 효과 O)
        s = s.replaceAll("(?i)ignore (all|previous) instructions", "[filtered]");
        s = s.replaceAll("(?i)system prompt", "[filtered]");
        s = s.replaceAll("(?i)developer message", "[filtered]");
        return s;
    }

    private String safeAiCall(String purpose, Supplier<String> supplier, String fallback) {
        try {
            String res = supplier.get();
            if (res == null || res.isBlank()) {
                return fallback;
            }
            return res;
        } catch (Exception e) {
            log.error("AI call failed. purpose={}, err={}", purpose, e.getMessage(), e);
            return fallback;
        }
    }

    // -----------------------------
    // 1) 음식 분석
    // -----------------------------
    public String analyzeFood(MultipartFile file, String foodName) {
        String fallbackJson = createDefaultJson();

        try {
            // content-type 1차 방어(이미지 아닌 파일 방지)
            if (file != null && !file.isEmpty()) {
                String ct = file.getContentType();
                if (ct == null || !ct.startsWith("image/")) {
                    return fallbackJson;
                }
            }

            String userHint = "";
            if (foodName != null && !foodName.isBlank()) {
                boolean isGarbage =
                        foodName.matches("^[0-9\\s!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]+$");
                if (!isGarbage) {
                    userHint = " (사용자가 제공한 힌트: '" + sanitizeForPrompt(foodName)
                            + "'. 단, 이 힌트가 사진과 명확히 다르다면 무시하세요.)";
                }
            }

            String systemInstruction = """
                    당신은 전문 영양사입니다. 음식 사진을 분석하여 영양 정보를 추정하세요.
                    
                    [분석 규칙]
                    1. 사용자의 힌트가 있더라도, 사진 속 음식과 다르면 사진을 최우선으로 분석하세요.
                    2. 음식 이름은 한국인이 흔히 쓰는 한글 명칭으로 작성하세요.
                    3. 응답은 오직 아래 JSON 포맷으로만 작성하세요. 마크다운/잡담 금지.
                    
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
            } else {
                if (userHint.isBlank()) {
                    return fallbackJson;
                }
                String promptText =
                        "음식 사진은 없어." + userHint + " 일반적인 1인분 기준으로 분석해줘.\n" + systemInstruction;
                aiResponseRaw =
                        chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
            }

            String cleanJson = cleanJsonOutput(aiResponseRaw);
            objectMapper.readTree(cleanJson); // 파싱 테스트
            return cleanJson;

        } catch (Exception e) {
            log.error("AI analyzeFood failed: {}", e.getMessage(), e);
            return fallbackJson;
        }
    }

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

    private byte[] compressImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IllegalArgumentException("이미지 파일이 아닙니다.");
        }

        long pixels = (long) originalImage.getWidth() * (long) originalImage.getHeight();
        if (pixels > MAX_IMAGE_PIXELS) {
            throw new IllegalArgumentException("이미지 해상도가 너무 큽니다.");
        }

        int targetWidth = 512;
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

    // -----------------------------
    // 2) 다음 끼니 추천 (✅ 500 방지)
    // -----------------------------
    public String recommendNextMeal(Long userId) {
        String today = LocalDate.now().toString();
        List<DietDto> logs = dietDao.selectDailyDiets(userId, today);

        String prompt;
        if (logs == null || logs.isEmpty()) {
            prompt = "오늘 기록된 식사가 없어. 가볍고 건강한 메뉴 3가지를 추천해줘.";
        } else {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(logs.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < limit; i++) {
                DietDto log = logs.get(i);
                String menuName = (log.getMemo() != null && !log.getMemo().isEmpty())
                        ? log.getMemo() : log.getMealType();
                menuName = sanitizeForPrompt(menuName);

                double kcal = (log.getTotalKcal() != null) ? log.getTotalKcal() : 0.0;
                sb.append(menuName).append("(").append((int) kcal).append("kcal), ");
            }
            prompt = "오늘 먹은 음식: " + sb + ". 부족한 영양소를 추측해서 다음 끼니 메뉴 3가지를 추천해줘.";
        }

        String finalPrompt = sanitizeForPrompt(prompt);
        return safeAiCall("recommendNextMeal",
                () -> visionClient.prompt().user(finalPrompt).call().content(),
                "추천 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    // -----------------------------
    // 3) 일간 리포트 (✅ 500 방지)
    // -----------------------------
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
        if (dailyLogs == null || dailyLogs.isEmpty()) {
            aiMessage = "기록된 식단이 없습니다. 오늘의 식사를 기록해보세요!";
        } else {
            StringBuilder sb = new StringBuilder("오늘 식단 리스트:\n");
            double totalKcal = 0;

            int limit = Math.min(dailyLogs.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < limit; i++) {
                DietDto d = dailyLogs.get(i);
                String menuName = (d.getMemo() != null && !d.getMemo().isEmpty()) ? d.getMemo() :
                        d.getMealType();
                menuName = sanitizeForPrompt(menuName);

                double kcal = (d.getTotalKcal() != null) ? d.getTotalKcal() : 0.0;
                sb.append("- ").append(menuName).append(" (").append((int) kcal).append("kcal)\n");
                totalKcal += kcal;
            }

            String prompt = sb + "\n총 " + (int) totalKcal + "kcal. 영양 균형 조언 3줄 요약해줘.";
            aiMessage = safeAiCall("dailyReport",
                    () -> visionClient.prompt().user(sanitizeForPrompt(prompt)).call().content(),
                    "리포트 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        if (dailyLogs != null && !dailyLogs.isEmpty()) {
            reportDao.insertReportLog(ReportLogDto.builder()
                    .userId(userId).reportType("DAILY").startDate(date).endDate(date)
                    .scoreAverage(0.0).aiMessage(aiMessage).build());
        }

        return AiReportResponse.builder()
                .type("DAILY").dateRange(date).dailyMeals(dailyLogs)
                .aiAnalysis(aiMessage).build();
    }

    // -----------------------------
    // 4) 주간/월간 리포트 (✅ 데이터 없으면 문장 + 저장X + 500 방지)
    // -----------------------------
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
        List<Integer> scores = (logs == null ? List.<DietDto>of() : logs).stream()
                .map(DietDto::getScore)
                .filter(s -> s != null && s > 0)
                .collect(Collectors.toList());

        if (logs == null || logs.isEmpty() || scores.isEmpty()) {
            String msg = type.equals("WEEKLY")
                    ? "최근 1주일간 기록이 없어 주간 분석을 생성할 수 없습니다. 식단을 기록해보세요!"
                    : "최근 1개월간 기록이 없어 월간 분석을 생성할 수 없습니다. 식단을 기록해보세요!";

            return AiReportResponse.builder()
                    .type(type)
                    .dateRange(sDate + " ~ " + eDate)
                    .averageScore(0.0)
                    .scoreTrend(scores)
                    .aiAnalysis(msg)
                    .build();
        }

        double avgScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        avgScore = Math.round(avgScore * 10) / 10.0;

        String periodName = type.equals("WEEKLY") ? "지난 1주" : "지난 1달";

        String prompt = """
                너는 식단 점수 데이터를 기반으로 %s 리포트를 작성하는 코치다.
                
                [규칙]
                - 점수리스트에 근거해서만 말해라. 과장/추측 금지.
                - 6~10줄 이내 한국어로만 작성.
                - 제목/마크다운/코드블록/이모지 금지.
                
                [입력]
                기간: %s
                평균점수: %.1f
                점수리스트: %s
                """.formatted(type.equals("WEEKLY") ? "주간" : "월간",
                periodName, avgScore, scores.toString());

        String aiMessage = safeAiCall("periodReport",
                () -> reportClient.prompt().user(sanitizeForPrompt(prompt)).call().content(),
                "리포트 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");

        reportDao.insertReportLog(ReportLogDto.builder()
                .userId(userId).reportType(type).startDate(sDate).endDate(eDate)
                .scoreAverage(avgScore).aiMessage(aiMessage).build());

        return AiReportResponse.builder()
                .type(type).dateRange(sDate + " ~ " + eDate)
                .averageScore(avgScore).scoreTrend(scores).aiAnalysis(aiMessage).build();
    }

    // -----------------------------
    // 5) 챌린지 추천 (✅ 파싱 실패/AI 실패 안전화)
    // -----------------------------
    public List<ChallengePresetDto> recommendGroupChallenges(List<String> keywords) {
        String keywordStr = (keywords == null || keywords.isEmpty())
                ? "건강, 운동"
                : sanitizeForPrompt(String.join(", ", keywords));

        String prompt = """
                주제: [%s]. 그룹 챌린지 주제 3가지를 추천해줘.
                응답은 오직 JSON 배열 포맷으로만:
                [{"title":"제목","content":"내용","goalType":"COUNT","targetCount":5,"keyword":"키워드"}]
                """.formatted(keywordStr);

        String jsonResult = safeAiCall("recommendChallenges",
                () -> visionClient.prompt().user(prompt).call().content(),
                "[]");

        jsonResult = cleanJsonOutput(jsonResult);

        try {
            ChallengePresetDto[] array =
                    objectMapper.readValue(jsonResult, ChallengePresetDto[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            log.error("챌린지 추천 파싱 실패 err={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // -----------------------------
    // 6) Gap Analysis (✅ 500 방지)
    // -----------------------------
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
                                "내가 부족한 점과 잘한 점을 비교 분석하고 동기부여해줘. 3줄 요약.",
                        myScore, myKcal, rankerScore, rankerKcal, goalKcal
                );

                aiMessage = safeAiCall("gapAnalysis",
                        () -> reportClient.prompt().user(sanitizeForPrompt(prompt)).call()
                                .content(),
                        "비교 분석 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");

                // 데이터가 있는 경우에만 저장(기존 정책)
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
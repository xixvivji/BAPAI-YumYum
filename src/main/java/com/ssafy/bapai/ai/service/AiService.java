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
import com.ssafy.bapai.member.dto.MemberDto;
import com.ssafy.bapai.member.dto.MemberGoalDto;
import com.ssafy.bapai.member.service.HealthService;
import com.ssafy.bapai.member.service.MemberService;
import com.ssafy.bapai.member.service.OptionService;
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

    // ✅ 프롬프트/리소스 방어용 상수
    private static final int MAX_PROMPT_TEXT_LEN = 800;
    private static final int MAX_LIST_ITEMS = 50;
    private static final long MAX_IMAGE_PIXELS = 4096L * 4096L; // DoS 완화용(약 16MP)

    private static final String FALLBACK_REPORT_MSG = "리포트 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";
    private static final String FALLBACK_RECOMMEND_MSG = "추천 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";
    private static final String FALLBACK_GAP_MSG = "비교 분석 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";

    private final ObjectMapper objectMapper;
    private final ReportDao reportDao;
    private final DietDao dietDao;
    private final ChatClient visionClient;
    private final ChatClient reportClient;
    private final ChatModel chatModel;
    private final MemberService memberService;
    private final OptionService optionService;
    private final HealthService healthService;

    public AiService(ObjectMapper objectMapper,
                     ReportDao reportDao,
                     DietDao dietDao,
                     ChatModel chatModel,
                     @Qualifier("visionChatClient") ChatClient visionClient,
                     @Qualifier("reportChatClient") ChatClient reportClient,
                     MemberService memberService,
                     OptionService optionService,
                     HealthService healthService
    ) {
        this.objectMapper = objectMapper;
        this.reportDao = reportDao;
        this.dietDao = dietDao;
        this.visionClient = visionClient;
        this.reportClient = reportClient;
        this.chatModel = chatModel;
        this.memberService = memberService;
        this.optionService = optionService;
        this.healthService = healthService;
    }

    // -----------------------------
    // ✅ 공통 방어 유틸
    // -----------------------------
    private String sanitizeForPrompt(String input) {
        if (input == null) {
            return "";
        }
        String s = input.replaceAll("\\p{Cntrl}", " ").trim();
        if (s.length() > MAX_PROMPT_TEXT_LEN) {
            s = s.substring(0, MAX_PROMPT_TEXT_LEN) + "...";
        }
        // 아주 단순한 인젝션 완화(완벽 X)
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

    /**
     * 1. 음식 분석
     * - content-type 검사
     * - 이미지 픽셀 상한 검사
     * - 예외 시 fallback JSON
     */
    public String analyzeFood(MultipartFile file, String foodName) {
        String fallbackJson = createDefaultJson();

        try {
            // ✅ 이미지 파일 여부 1차 검사
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
                    2. 음식 이름은 반드시 한국인이 흔히 쓰는 한글 명칭으로 작성하세요.
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
            objectMapper.readTree(cleanJson);
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

        // ✅ 픽셀 수 상한 (초고해상도 DoS 완화)
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

    // 2) 다음 끼니 추천 (✅ 500 방지 + 토큰 방어)
    public String recommendNextMeal(Long userId) {
        // 1. 기본 회원 정보(+질병/알러지) 조회
        MemberDto member = memberService.getMember(userId); // 신체, 질환, 알레르기 포함
        MemberGoalDto metrics = healthService.calculateHealthMetrics(member); // 권장 섭취량

        String diseaseLabel = optionService.diseaseNames(member.getDiseaseIds());
        String allergyLabel = optionService.allergyNames(member.getAllergyIds());
        String gender = "M".equalsIgnoreCase(member.getGender()) ? "남성" : "여성";

        // 아래 두 변수는 Double일 수 있으니 주의! (Double → int: NullPointer 방지)
        int age = (member.getBirthYear() == null) ? 0 :
                LocalDate.now().getYear() - member.getBirthYear();
        int height = (member.getHeight() == null) ? 0 : (int) Math.round(member.getHeight());
        int weight = (member.getWeight() == null) ? 0 : (int) Math.round(member.getWeight());

        String activityLabel = activityLevelKor(member.getActivityLevel());
        String goalLabel = dietGoalKor(member.getDietGoal());

        long recKcal = Math.round(metrics.getRecCalories());
        long recCarbs = Math.round(metrics.getRecCarbs());
        long recProtein = Math.round(metrics.getRecProtein());
        long recFat = Math.round(metrics.getRecFat());

        // 2. 오늘 식단 기록 불러오기 (기존 로직 유지)
        String today = LocalDate.now().toString();
        List<DietDto> logs = dietDao.selectDailyDiets(userId, today);

        StringBuilder foodHistory = new StringBuilder();
        if (logs != null && !logs.isEmpty()) {
            int limit = Math.min(logs.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < limit; i++) {
                DietDto log = logs.get(i);
                String menuName =
                        (log.getMemo() != null && !log.getMemo().isEmpty()) ? log.getMemo() :
                                log.getMealType();
                menuName = sanitizeForPrompt(menuName);
                double kcal = (log.getTotalKcal() != null) ? log.getTotalKcal() : 0.0;
                foodHistory.append(menuName).append("(").append((int) Math.round(kcal))
                        .append("kcal), ");
            }
        }

        // 3. 디테일 프롬프트 구성
        String prompt = String.format(
                "내 신체정보는 %d세 %s(키 %dcm, 몸무게 %dkg), 활동량 %s, 식이목표: %s, 질환: %s, 알레르기: %s야. "
                        + "1일 권장 섭취량은 %d kcal(탄수 %d g, 단백 %d g, 지방 %d g)이야. ",
                age, gender, height, weight,
                activityLabel, goalLabel, diseaseLabel, allergyLabel,
                recKcal, recCarbs, recProtein, recFat
        );

        if (foodHistory.length() == 0) {
            prompt += "오늘 식사 이력이 없어. 내 건강 상태를 반영해서 다음 끼니로 가볍고 건강한 메뉴 3가지를 (질환/알러지 유의하여) 추천해줘.";
        } else {
            prompt += "오늘 먹은 음식: " + foodHistory +
                    "부족할 수 있는 영양소와 내 몸 상태(질환/알레르기/신체)를 종합해 다음 끼니 메뉴 3가지(가능하면 한식 위주로, 금지성분은 반드시 빼고) 추천해줘.";
        }

        String finalPrompt = sanitizeForPrompt(prompt);
        return safeAiCall("recommendNextMeal",
                () -> visionClient.prompt().user(finalPrompt).call().content(),
                FALLBACK_RECOMMEND_MSG);
    }

    // 한글 변환 예시 메서드 (실 프로젝트 용어에 맞게)
    private String activityLevelKor(String level) {
        if ("LOW".equals(level)) {
            return "낮음";
        }
        if ("NORMAL".equals(level)) {
            return "보통";
        }
        if ("HIGH".equals(level)) {
            return "높음";
        }
        return "미상";
    }

    private String dietGoalKor(String goal) {
        if ("LOSS".equals(goal)) {
            return "감량";
        }
        if ("GAIN".equals(goal)) {
            return "증량";
        }
        return "유지";
    }

    // 3) 일간 리포트 (✅ 500 방지)
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
            // ===== 1. 개인 건강 정보 준비 =====
            MemberDto member = memberService.getMember(userId);
            MemberGoalDto goal = healthService.calculateHealthMetrics(member);

            String diseaseLabel = optionService.diseaseNames(member.getDiseaseIds());
            String allergyLabel = optionService.allergyNames(member.getAllergyIds());
            String gender = "M".equalsIgnoreCase(member.getGender()) ? "남성" : "여성";
            int age = LocalDate.now().getYear() - member.getBirthYear();
            int height = member.getHeight() != null ? member.getHeight().intValue() : 0;
            int weight = member.getWeight() != null ? member.getWeight().intValue() : 0;
            String activityLabel = activityLevelKor(member.getActivityLevel());
            String goalLabel = dietGoalKor(member.getDietGoal());

            long recKcal = Math.round(goal.getRecCalories());
            long recCarbs = Math.round(goal.getRecCarbs());
            long recProtein = Math.round(goal.getRecProtein());
            long recFat = Math.round(goal.getRecFat());

            // ===== 2. 오늘 먹은 음식/칼로리 등 기존 요약 =====
            StringBuilder sb = new StringBuilder("오늘 먹은 식사 내역:\n");
            double totalKcal = 0;
            int limit = Math.min(dailyLogs.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < limit; i++) {
                DietDto d = dailyLogs.get(i);
                String menuName = (d.getMemo() != null && !d.getMemo().isEmpty()) ? d.getMemo() :
                        d.getMealType();
                menuName = sanitizeForPrompt(menuName);
                double kcal = (d.getTotalKcal() != null) ? d.getTotalKcal() : 0.0;
                sb.append("- ").append(menuName).append(" (").append((int) Math.round(kcal))
                        .append("kcal)\n");
                totalKcal += kcal;
            }

            // ===== 3. 디테일 프롬프트 세팅 =====
            String prompt = String.format(
                    "내 신체정보는 %d세 %s(키 %dcm, 몸무게 %dkg), 활동량 %s, 목표: %s, 질환: %s, 알레르기: %s.\n" +
                            "1일 권장 섭취: %d kcal (탄수 %d g, 단백 %d g, 지방 %d g).\n\n" +
                            "%s\n" +
                            "총 섭취 칼로리: %d kcal.\n" +
                            "오늘 내 식단을 건강/균형/질병 및 알레르기 관점에서 분석해서, 부족 or 과한 영양소와 유의사항, 건강 개선 TIP을 각각 한 문장씩(총 3문장) 조언해줘. (의학/영양사 관점으로 현실적으로.)",
                    age, gender, height, weight, activityLabel, goalLabel, diseaseLabel,
                    allergyLabel,
                    recKcal, recCarbs, recProtein, recFat,
                    sb, (int) Math.round(totalKcal)
            );

            aiMessage = safeAiCall("dailyReport",
                    () -> visionClient.prompt().user(sanitizeForPrompt(prompt)).call().content(),
                    FALLBACK_REPORT_MSG);
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


    @Transactional
    public AiReportResponse getPeriodReport(Long userId, String type) {
        LocalDate end = LocalDate.now();
        LocalDate start = type.equals("WEEKLY") ? end.minusWeeks(1) : end.minusMonths(1);
        String sDate = start.toString();
        String eDate = end.toString();

        ReportLogDto cachedLog = reportDao.selectExistingReport(userId, type, sDate, eDate);

        if (cachedLog != null && !isFallbackMessage(cachedLog.getAiMessage())) {
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

        // ========== [추가] 내 신체/건강/권장치 정보 ==========
        MemberDto member = memberService.getMember(userId);
        MemberGoalDto goal = healthService.calculateHealthMetrics(member);

        String diseaseLabel = optionService.diseaseNames(member.getDiseaseIds());
        String allergyLabel = optionService.allergyNames(member.getAllergyIds());
        String gender = "M".equalsIgnoreCase(member.getGender()) ? "남성" : "여성";
        int age = LocalDate.now().getYear() - member.getBirthYear();
        int height = member.getHeight() != null ? member.getHeight().intValue() : 0;
        int weight = member.getWeight() != null ? member.getWeight().intValue() : 0;
        String activityLabel = activityLevelKor(member.getActivityLevel());
        String goalLabel = dietGoalKor(member.getDietGoal());

        long recKcal = Math.round(goal.getRecCalories());
        long recCarbs = Math.round(goal.getRecCarbs());
        long recProtein = Math.round(goal.getRecProtein());
        long recFat = Math.round(goal.getRecFat());

        // =========== [추가] 식사 총계/평균 등 통계 ===========
        int mealCount = logs.size();
        double totalKcal =
                logs.stream().mapToDouble(d -> (d.getTotalKcal() != null) ? d.getTotalKcal() : 0.0)
                        .sum();
        double avgKcal = mealCount == 0 ? 0 : Math.round(totalKcal / mealCount);

        String prompt = String.format(
                "내 신체: %d세 %s(키 %dcm, 몸무게 %dkg), 활동량 %s, 목표 %s, 질환: %s, 알레르기: %s.\n" +
                        "1일 권장 섭취: %d kcal (탄수 %d g, 단백 %d g, 지방 %d g)\n" +
                        "[입력]\n" +
                        "%s 동안의 식사 횟수: %d, 총섭취 칼로리(합): %d kcal, 1회 평균 섭취: %d kcal\n" +
                        "평균 식단 점수: %.1f\n" +
                        "점수 리스트: %s\n\n" +
                        "[미션]\n" +
                        "%s간 내 '식사점수/영양/섭취패턴/나의 건강정보(질병·알러지 포함)'를 종합해,\n" +
                        "- 내 식습관 강점 1~2개\n" +
                        "- 영양상 부족/과잉/불균형 위험\n" +
                        "- 질환/알러지 관점 주의점\n" +
                        "- 실질적인 건강개선 팁/가이드 (식단 구성 힌트 포함)\n" +
                        "위 내용을 총 8줄 이내, 현실적이고 구체적인 한국어로 써줘. 의학적 코멘트 환영, 과장/너무 일반적 금지.",
                age, gender, height, weight, activityLabel, goalLabel, diseaseLabel, allergyLabel,
                recKcal, recCarbs, recProtein, recFat,
                periodName, mealCount, (int) Math.round(totalKcal), (int) avgKcal,
                avgScore, scores.toString(),
                periodName
        );

        String aiMessage = safeAiCall("periodReport",
                () -> reportClient.prompt().user(sanitizeForPrompt(prompt)).call().content(),
                FALLBACK_REPORT_MSG);

        if (!isFallbackMessage(aiMessage)) {
            reportDao.insertReportLog(ReportLogDto.builder()
                    .userId(userId).reportType(type).startDate(sDate).endDate(eDate)
                    .scoreAverage(avgScore).aiMessage(aiMessage).build());
        }

        return AiReportResponse.builder()
                .type(type).dateRange(sDate + " ~ " + eDate)
                .averageScore(avgScore).scoreTrend(scores).aiAnalysis(aiMessage).build();
    }

    // 5) 챌린지 추천 (✅ AI/파싱 실패 안전화)
    public List<ChallengePresetDto> recommendGroupChallenges(List<String> keywords) {
        String keywordStr =
                (keywords == null || keywords.isEmpty()) ? "건강, 운동" :
                        sanitizeForPrompt(String.join(", ", keywords));

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
            log.error("Challenge recommend parse failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // 6) Gap Analysis (✅ 500 방지)
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
        double goalKcal = 2000.0; // 목표 칼로리, 추후 MemberGoalDto에서 가져와도 됨

        ReportLogDto cached = reportDao.selectExistingReport(userId, "GAP_ANALYSIS", sDate, eDate);
        String aiMessage;

        if (cached != null) {
            aiMessage = cached.getAiMessage();
        } else {
            if (myScore == 0 && myKcal == 0) {
                aiMessage = "아직 충분한 식단 기록이 없습니다. 식단을 꾸준히 기록하면 분석해드릴게요!";
            } else {
                // ---- 내 건강정보/질병/알러지/목표까지 포함 --------
                MemberDto member = memberService.getMember(userId);
                MemberGoalDto goal = healthService.calculateHealthMetrics(member);

                String diseaseLabel = optionService.diseaseNames(member.getDiseaseIds());
                String allergyLabel = optionService.allergyNames(member.getAllergyIds());
                String gender = "M".equalsIgnoreCase(member.getGender()) ? "남성" : "여성";
                int age = LocalDate.now().getYear() - member.getBirthYear();
                int height = member.getHeight() != null ? member.getHeight().intValue() : 0;
                int weight = member.getWeight() != null ? member.getWeight().intValue() : 0;
                String activityLabel = activityLevelKor(member.getActivityLevel());
                String goalLabel = dietGoalKor(member.getDietGoal());

                long recKcal = Math.round(goal.getRecCalories());

                String periodName = "MONTHLY".equals(type) ? "지난 1개월" : "지난 1주일";

                String prompt = String.format(
                        "당신은 영양사/건강코치입니다.\n" +
                                "아래는 %s간 나와 그룹 랭커(상위권 사용자) 비교 분석 결과입니다.\n" +
                                "[나 정보] %d세 %s(키 %dcm, 몸무게 %dkg), 질환: %s, 알레르기: %s, 목표: %s, 권장칼로리: %d kcal\n" +
                                "[나] 평균점수: %.1f, 평균칼로리: %.0f kcal\n" +
                                "[랭커] 평균점수: %.1f, 평균칼로리: %.0f kcal\n" +
                                "[목표] 칼로리: %.0f kcal\n" +
                                "[미션] 점수·칼로리·내 신체·질환/알러지 등 내 정보를 반영하여\n" +
                                "- 내 식습관상 부족/강점 1~2줄\n" +
                                "- 랭커와 직접 비교했을 때 차이점/예시\n" +
                                "- 건강개선 actionable tip/멘트 1줄\n" +
                                "총 4줄을 현실감 있게 요약. 너무 추상적이거나 뻔하지 않게 당부.",
                        periodName, age, gender, height, weight, diseaseLabel, allergyLabel,
                        goalLabel, recKcal,
                        myScore, myKcal, rankerScore, rankerKcal, goalKcal
                );

                aiMessage = safeAiCall("gapAnalysis",
                        () -> reportClient.prompt().user(sanitizeForPrompt(prompt)).call()
                                .content(),
                        FALLBACK_GAP_MSG);

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

    private boolean isFallbackMessage(String msg) {
        if (msg == null) {
            return false;
        }
        return msg.equals(FALLBACK_REPORT_MSG)
                || msg.equals(FALLBACK_RECOMMEND_MSG)
                || msg.equals(FALLBACK_GAP_MSG);
    }
}
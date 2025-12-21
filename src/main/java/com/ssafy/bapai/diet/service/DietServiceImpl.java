package com.ssafy.bapai.diet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.ai.service.AiService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DailyDietLogDto;
import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.dto.DietLogItemDto;
import com.ssafy.bapai.diet.dto.StreakDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DietServiceImpl implements DietService {

    private final DietDao dietDao;
    private final S3Service s3Service;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @Override
    public DietDto analyzeDiet(MultipartFile file, String hint) {
        DietDto dietDto = new DietDto();
        // 1. 이미지 업로드
        if (file != null && !file.isEmpty()) {
            try {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl);
            } catch (Exception e) {
                log.error("이미지 업로드 실패", e);
                throw new RuntimeException("이미지 업로드 중 오류 발생");
            }
        }
        // 2. AI 분석 요청
        String aiResponse = aiService.analyzeFood(file, hint);

        // 3. 파싱 및 계산
        applyAiResult(dietDto, aiResponse);
        calculateTotalNutrition(dietDto);

        return dietDto;
    }

    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        calculateTotalNutrition(dietDto);
        dietDao.insertDiet(dietDto);
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
            }
            dietDao.insertDietDetails(dietDto.getFoodList());
        }
    }

    @Override
    @Transactional
    public void updateDiet(DietDto dietDto) {
        calculateTotalNutrition(dietDto);
        dietDao.updateDiet(dietDto);
        dietDao.deleteDietDetailsByDietId(dietDto.getDietId()); // 기존 상세 삭제
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
            }
            dietDao.insertDietDetails(dietDto.getFoodList()); // 새 상세 저장
        }
    }

    @Override
    @Transactional
    public void deleteDiet(Long dietId) {
        dietDao.deleteDiet(dietId);
    }

    @Override
    public DailyDietLogDto getDailyDietLog(Long userId, String date) {
        // 1. DB에서 데이터 가져오기 (기존 DAO 메서드 재사용)
        List<DietDto> originalList = dietDao.selectDailyDiets(userId, date);
        Map<String, Object> waterInfo = dietDao.selectWaterInfo(userId, date);

        int waterCount = (waterInfo != null) ?
                Integer.parseInt(String.valueOf(waterInfo.get("water_count"))) : 0;
        int waterGoal = (waterInfo != null) ?
                Integer.parseInt(String.valueOf(waterInfo.get("water_goal"))) : 8;

        // 2. 통계 계산 & 리스트 평탄화 (Flatten)
        double sumKcal = 0, sumCarbs = 0, sumProtein = 0, sumFat = 0;
        int mealCount = 0, snackCount = 0;

        // ★ 새로 만든 평탄화된 리스트
        List<DietLogItemDto> flatList = new ArrayList<>();

        for (DietDto diet : originalList) {
            // 통계 계산 (식사/간식 횟수)
            if ("SNACK".equalsIgnoreCase(diet.getMealType())) {
                snackCount++;
            } else {
                mealCount++;
            }

            // ★ 내부 음식 리스트를 꺼내서 바깥 리스트로 펼치기
            if (diet.getFoodList() != null) {
                for (DietDetailDto food : diet.getFoodList()) {
                    // 영양소 합산
                    sumKcal += (food.getKcal() != null ? food.getKcal() : 0);
                    sumCarbs += (food.getCarbs() != null ? food.getCarbs() : 0);
                    sumProtein += (food.getProtein() != null ? food.getProtein() : 0);
                    sumFat += (food.getFat() != null ? food.getFat() : 0);

                    // 평탄화 객체 생성 (원하시는 JSON 구조)
                    DietLogItemDto item = DietLogItemDto.builder()
                            .dietId(diet.getDietId())
                            .date(diet.getEatDate())
                            .foodName(food.getFoodName())
                            .kcal(food.getKcal() != null ? food.getKcal() : 0)
                            .carbs(food.getCarbs() != null ? food.getCarbs() : 0)
                            .protein(food.getProtein() != null ? food.getProtein() : 0)
                            .fat(food.getFat() != null ? food.getFat() : 0)
                            .mealType(diet.getMealType())
                            .time(null) // DB에 time 컬럼이 없으면 null, 있으면 diet.getTime()
                            .servings(food.getAmount())
                            .build();

                    flatList.add(item);
                }
            }
        }

        // 3. 결과 반환
        return DailyDietLogDto.builder()
                .date(date)
                .waterCupCount(waterCount)
                .waterGoal(waterGoal)
                .totalMealCount(mealCount)
                .totalSnackCount(snackCount)
                .totalCalories(Math.round(sumKcal * 10) / 10.0)
                .totalCarbs(Math.round(sumCarbs * 10) / 10.0)
                .totalProtein(Math.round(sumProtein * 10) / 10.0)
                .totalFat(Math.round(sumFat * 10) / 10.0)
                .dietList(flatList) // ★ 펼쳐진 리스트 넣기
                .build();
    }

    /**
     * 주간/월간 조회: 기간 내 날짜별로 Loop 돌면서 Map 생성 (Key: "1", "2" ...)
     */
    @Override
    public Map<String, DailyDietLogDto> getPeriodDietLogs(Long userId, String startDate,
                                                          String endDate) {
        Map<String, DailyDietLogDto> resultMap = new LinkedHashMap<>(); // 순서 보장 (1, 2, 3...)

        java.time.LocalDate start = java.time.LocalDate.parse(startDate);
        java.time.LocalDate end = java.time.LocalDate.parse(endDate);

        int index = 1;

        while (!start.isAfter(end)) {
            String currDate = start.toString();

            // 위에서 만든 하루치 로직 재사용 (코드 중복 방지)
            DailyDietLogDto dailyLog = getDailyDietLog(userId, currDate);

            resultMap.put(String.valueOf(index++), dailyLog);
            start = start.plusDays(1);
        }
        return resultMap;
    }

    /**
     * 스트릭 조회: 현재 연속 일수 & 역대 최장 연속 일수
     */
    @Override
    public StreakDto getDietStreak(Long userId) {
        List<String> dates = dietDao.selectDietDates(userId); // DB에서 내림차순 날짜 리스트 가져옴

        if (dates.isEmpty()) {
            return StreakDto.builder().currentStreak(0).longestStreak(0).build();
        }

        // 1. 현재 스트릭(Current Streak) 계산
        int currentStreak = 0;
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate checkDate = today;

        java.util.Set<String> dateSet = new java.util.HashSet<>(dates); // 검색 속도 향상

        // 오늘 기록이 없으면 어제부터 체크 (어제 썼으면 스트릭 유지)
        if (!dateSet.contains(today.toString())) {
            checkDate = today.minusDays(1);
        }

        // 과거로 가면서 연속된 날짜 카운트
        while (dateSet.contains(checkDate.toString())) {
            currentStreak++;
            checkDate = checkDate.minusDays(1);
        }

        // 2. 최장 스트릭(Longest Streak) 계산
        int longestStreak = 0;
        int tempStreak = 1;

        // 날짜 정렬 (오름차순)
        List<java.time.LocalDate> sortedDates = dates.stream()
                .map(java.time.LocalDate::parse)
                .sorted()
                .collect(Collectors.toList());

        if (!sortedDates.isEmpty()) {
            longestStreak = 1; // 기록이 하나라도 있으면 최소 1
        }

        for (int i = 0; i < sortedDates.size() - 1; i++) {
            java.time.LocalDate curr = sortedDates.get(i);
            java.time.LocalDate next = sortedDates.get(i + 1);

            if (curr.plusDays(1).equals(next)) {
                tempStreak++;
            } else {
                longestStreak = Math.max(longestStreak, tempStreak);
                tempStreak = 1; // 끊기면 리셋
            }
        }
        longestStreak = Math.max(longestStreak, tempStreak);

        return StreakDto.builder()
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .build();
    }

    @Override
    @Transactional
    public void changeWaterCount(Long userId, String date, int delta) {
        dietDao.updateWaterCountDelta(userId, date, delta);
    }

    @Override
    @Transactional
    public void changeWaterGoal(Long userId, String date, int delta) {
        dietDao.updateWaterGoalDelta(userId, date, delta);
    }

    // =================================================================================
    // 4. [기타] 조회 및 헬퍼 메서드
    // =================================================================================

    @Override
    public List<DietDto> getAllDiets(Long userId) {
        return dietDao.selectAllDiets(userId);
    }

    @Override
    public DietDto getDietDetail(Long dietId) {
        return dietDao.selectDietDetail(dietId);
    }

    @Override
    public PageResponse<DietDto> getDietFeed(String sort, int size, int page) {
        int offset = (page - 1) * size;
        List<DietDto> content = dietDao.selectDietList(sort, size, offset);
        int totalElements = dietDao.selectDietCount();
        return new PageResponse<>(content, page, size, totalElements);
    }

    @Override
    public int getDietCount() {
        return dietDao.selectDietCount();
    }

    // ★ 헬퍼: 영양소 총합 계산
    private void calculateTotalNutrition(DietDto dto) {
        if (dto.getFoodList() == null || dto.getFoodList().isEmpty()) {
            return;
        }

        double sumKcal = 0, sumCarbs = 0, sumProtein = 0, sumFat = 0;

        for (DietDetailDto food : dto.getFoodList()) {
            sumKcal += (food.getKcal() != null ? food.getKcal() : 0);
            sumCarbs += (food.getCarbs() != null ? food.getCarbs() : 0);
            sumProtein += (food.getProtein() != null ? food.getProtein() : 0);
            sumFat += (food.getFat() != null ? food.getFat() : 0);
        }

        dto.setTotalKcal(round(sumKcal));
        dto.setTotalCarbs(round(sumCarbs));
        dto.setTotalProtein(round(sumProtein));
        dto.setTotalFat(round(sumFat));
    }

    // ★ 헬퍼: AI 결과 파싱
    private void applyAiResult(DietDto dietDto, String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);
            List<DietDetailDto> foodList = new ArrayList<>();

            if (root.has("foodList") && root.get("foodList").isArray()) {
                for (JsonNode node : root.get("foodList")) {
                    DietDetailDto detail = new DietDetailDto();
                    detail.setFoodName(node.path("foodName").asText("알 수 없음"));
                    detail.setKcal(node.path("kcal").asDouble(0));
                    detail.setCarbs(node.path("carbs").asDouble(0));
                    detail.setProtein(node.path("protein").asDouble(0));
                    detail.setFat(node.path("fat").asDouble(0));
                    detail.setAmount(node.path("amount").asInt(1));
                    foodList.add(detail);
                }
                dietDto.setFoodList(foodList);
            }
            if (root.has("aiAnalysis")) {
                dietDto.setAiAnalysis(root.get("aiAnalysis").asText());
            }
            if (root.has("score")) {
                dietDto.setScore(root.get("score").asInt());
            }

            // 메모가 비어있으면 첫 음식명으로 자동 채움
            if ((dietDto.getMemo() == null || dietDto.getMemo().isBlank()) && !foodList.isEmpty()) {
                dietDto.setMemo(foodList.get(0).getFoodName());
            }

        } catch (Exception e) {
            log.error("AI 응답 파싱 실패", e);
        }
    }

    // 소수점 반올림 (소수점 첫째 자리까지)
    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
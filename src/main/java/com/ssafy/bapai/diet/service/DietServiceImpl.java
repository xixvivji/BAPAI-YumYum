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
import com.ssafy.bapai.diet.dto.PeriodDietLogDto;
import com.ssafy.bapai.diet.dto.StreakDto;
import java.util.ArrayList;
import java.util.Collections;
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

    // =================================================================================
    // 1. 저장 및 분석
    // =================================================================================

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
        // 2. AI 분석
        String aiResponse = aiService.analyzeFood(file, hint);

        // 3. 결과 적용
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


    // =================================================================================
    // 2. 조회 (핵심 로직)
    // =================================================================================

    /**
     * 일간 조회: 상세 리스트 + 이미지 포함 (selectDailyDiets 사용)
     */
    @Override
    public DailyDietLogDto getDailyDietLog(Long userId, String date) {
        // 1. DB 조회 (이미지 포함된 쿼리)
        List<DietDto> originalList = dietDao.selectDailyDiets(userId, date);
        Map<String, Object> waterInfo = dietDao.selectWaterInfo(userId, date);

        int waterCount = (waterInfo != null) ?
                Integer.parseInt(String.valueOf(waterInfo.get("water_count"))) : 0;
        int waterGoal = (waterInfo != null) ?
                Integer.parseInt(String.valueOf(waterInfo.get("water_goal"))) : 8;

        // 2. 통계 계산 및 DTO 변환
        return calculateDailyStats(originalList, date, waterCount, waterGoal);
    }

    /**
     * 기간(주간/월간) 조회: Loop 없이 한 번에 조회 + 이미지 제외 (selectWeeklyDiets 사용)
     */
    @Override
    public PeriodDietLogDto getPeriodDietLogs(Long userId, String startDate, String endDate) {
        // 1. DB 조회 (한 번에 다 가져옴, 이미지는 XML에서 제외됨)
        List<DietDto> fullList = dietDao.selectWeeklyDiets(userId, startDate, endDate);

        // 2. 날짜별로 그룹핑 (Key: "YYYY-MM-DD")
        Map<String, List<DietDto>> groupedByDate = fullList.stream()
                .collect(Collectors.groupingBy(DietDto::getEatDate));

        // 3. 결과 맵 생성
        Map<String, DailyDietLogDto> resultMap = new LinkedHashMap<>();

        java.time.LocalDate start = java.time.LocalDate.parse(startDate);
        java.time.LocalDate end = java.time.LocalDate.parse(endDate);

        double totalKcal = 0, totalCarbs = 0, totalProtein = 0, totalFat = 0;
        int totalMeal = 0, totalSnack = 0;
        int index = 1; // 1일차, 2일차... 인덱스

        while (!start.isAfter(end)) {
            String currDate = start.toString();
            List<DietDto> dayList = groupedByDate.getOrDefault(currDate, Collections.emptyList());

            // 해당 날짜의 통계 계산 (물 정보는 기간 조회시엔 보통 0이나 기본값 처리, 필요하면 추가 조회)
            DailyDietLogDto dailyStat = calculateDailyStats(dayList, currDate, 0, 0);

            // 전체 합계 누적
            totalKcal += dailyStat.getTotalCalories();
            totalCarbs += dailyStat.getTotalCarbs();
            totalProtein += dailyStat.getTotalProtein();
            totalFat += dailyStat.getTotalFat();
            totalMeal += dailyStat.getTotalMealCount();
            totalSnack += dailyStat.getTotalSnackCount();

            // 결과 맵에 추가
            resultMap.put(String.valueOf(index++), dailyStat);

            start = start.plusDays(1);
        }

        return PeriodDietLogDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalMealCount(totalMeal)
                .totalSnackCount(totalSnack)
                .totalCalories(round(totalKcal))
                .totalCarbs(round(totalCarbs))
                .totalProtein(round(totalProtein))
                .totalFat(round(totalFat))
                .dailyLogs(resultMap)
                .build();
    }

    // ★ [공통 로직 분리] 리스트 -> DailyDietLogDto 변환
    private DailyDietLogDto calculateDailyStats(List<DietDto> dietList, String date, int waterCount,
                                                int waterGoal) {
        double sumKcal = 0, sumCarbs = 0, sumProtein = 0, sumFat = 0;
        int mealCount = 0, snackCount = 0;
        List<DietLogItemDto> flatList = new ArrayList<>();

        for (DietDto diet : dietList) {
            if ("SNACK".equalsIgnoreCase(diet.getMealType())) {
                snackCount++;
            } else {
                mealCount++;
            }

            if (diet.getFoodList() != null) {
                for (DietDetailDto food : diet.getFoodList()) {
                    sumKcal += (food.getKcal() != null ? food.getKcal() : 0);
                    sumCarbs += (food.getCarbs() != null ? food.getCarbs() : 0);
                    sumProtein += (food.getProtein() != null ? food.getProtein() : 0);
                    sumFat += (food.getFat() != null ? food.getFat() : 0);

                    flatList.add(DietLogItemDto.builder()
                            .dietId(diet.getDietId())
                            .date(diet.getEatDate())
                            .foodName(food.getFoodName())
                            .kcal(food.getKcal() != null ? food.getKcal() : 0)
                            .carbs(food.getCarbs() != null ? food.getCarbs() : 0)
                            .protein(food.getProtein() != null ? food.getProtein() : 0)
                            .fat(food.getFat() != null ? food.getFat() : 0)
                            .mealType(diet.getMealType())
                            .time(diet.getTime())
                            .servings(food.getAmount())
                            .imgUrl(diet.getDietImg()) // 일일조회면 있고, 기간조회면 null임
                            .build());
                }
            }
        }

        return DailyDietLogDto.builder()
                .date(date)
                .waterCupCount(waterCount)
                .waterGoal(waterGoal)
                .totalMealCount(mealCount)
                .totalSnackCount(snackCount)
                .totalCalories(round(sumKcal))
                .totalCarbs(round(sumCarbs))
                .totalProtein(round(sumProtein))
                .totalFat(round(sumFat))
                .dietList(flatList)
                .build();
    }


    // =================================================================================
    // 3. 스트릭 및 물 관리
    // =================================================================================

    @Override
    public StreakDto getDietStreak(Long userId) {
        List<String> dates = dietDao.selectDietDates(userId);

        if (dates.isEmpty()) {
            return StreakDto.builder().currentStreak(0).longestStreak(0).build();
        }

        // 1. 현재 스트릭
        int currentStreak = 0;
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate checkDate = today;
        java.util.Set<String> dateSet = new java.util.HashSet<>(dates);

        if (!dateSet.contains(today.toString())) {
            checkDate = today.minusDays(1);
        }

        while (dateSet.contains(checkDate.toString())) {
            currentStreak++;
            checkDate = checkDate.minusDays(1);
        }

        // 2. 최장 스트릭
        int longestStreak = 0;
        int tempStreak = 1;
        List<java.time.LocalDate> sortedDates = dates.stream()
                .map(java.time.LocalDate::parse)
                .sorted()
                .collect(Collectors.toList());

        if (!sortedDates.isEmpty()) {
            longestStreak = 1;
        }

        for (int i = 0; i < sortedDates.size() - 1; i++) {
            if (sortedDates.get(i).plusDays(1).equals(sortedDates.get(i + 1))) {
                tempStreak++;
            } else {
                longestStreak = Math.max(longestStreak, tempStreak);
                tempStreak = 1;
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
    // 4. 기타 조회 및 헬퍼
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

    // 영양소 합계 계산
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

            if ((dietDto.getMemo() == null || dietDto.getMemo().isBlank()) && !foodList.isEmpty()) {
                dietDto.setMemo(foodList.get(0).getFoodName());
            }
        } catch (Exception e) {
            log.error("AI 응답 파싱 실패", e);
        }
    }

    @Transactional
    public void insertDiet(DietDto dietDto, Long userId) {
        // AI로부터 개인별 TDEE 기반 점수 획득
        int aiScore = aiService.calculateDietScore(userId, dietDto);

        dietDto.setUserId(userId);
        dietDto.setScore(aiScore); // 산출된 점수 세팅

        dietDao.insertDiet(dietDto); // DB 저장 (이 점수가 랭킹에 쓰임)
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
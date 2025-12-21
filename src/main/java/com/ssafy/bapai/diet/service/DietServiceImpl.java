package com.ssafy.bapai.diet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.ai.service.AiService;
import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.ArrayList;
import java.util.List;
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

    // 1. 분석 API 로직 (저장 X, DTO 리턴 O)
    @Override
    public DietDto analyzeDiet(MultipartFile file, String hint) {
        DietDto dietDto = new DietDto();

        // (1) 이미지 S3 업로드 (미리 올려두고 URL을 획득)
        if (file != null && !file.isEmpty()) {
            try {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl);
            } catch (Exception e) {
                log.error("이미지 업로드 실패", e);
                throw new RuntimeException("이미지 업로드 중 오류 발생");
            }
        }

        // (2) AiService 호출 (힌트가 없으면 빈 문자열 처리)
        // AiService가 JSON String을 반환한다고 가정
        String aiResponse = aiService.analyzeFood(file, hint);

        // (3) 결과 파싱 (JSON String -> DTO의 foodList 세팅)
        applyAiResult(dietDto, aiResponse);

        // (4) 총합 계산 (프론트에 보여주기 위해 미리 계산)
        calculateTotalNutrition(dietDto);

        // DB 저장 없이 분석 결과 DTO만 리턴
        return dietDto;
    }


    // 2. 순수 DB 저장 (메인 + 상세 한방 저장)
    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        // (1) 영양소 총합 재계산 (사용자가 수량 등을 수정했을 수 있음)
        calculateTotalNutrition(dietDto);

        // (2) diet 테이블 저장
        dietDao.insertDiet(dietDto);

        // (3) diet_detail 테이블 저장
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
            }
            dietDao.insertDietDetails(dietDto.getFoodList());
        }
    }

    // 3. 식단 수정
    @Override
    @Transactional
    public void updateDiet(DietDto dietDto) {
        // 총합 재계산 (수정된 음식 리스트 기준)
        calculateTotalNutrition(dietDto);

        // 메인 정보 수정
        dietDao.updateDiet(dietDto);

        // 기존 상세 내역 삭제
        dietDao.deleteDietDetailsByDietId(dietDto.getDietId());

        // 새로운 상세 내역 저장
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
            }
            dietDao.insertDietDetails(dietDto.getFoodList()); // 한방 쿼리
        }
    }

    // ★ 헬퍼: 영양소 총합 계산
    private void calculateTotalNutrition(DietDto dto) {
        if (dto.getFoodList() == null || dto.getFoodList().isEmpty()) {
            // 음식 목록이 없으면 0으로 초기화 (또는 기존 값 유지)
            return;
        }

        double sumKcal = 0;
        double sumCarbs = 0;
        double sumProtein = 0;
        double sumFat = 0;

        for (DietDetailDto food : dto.getFoodList()) {
            sumKcal += (food.getKcal() != null ? food.getKcal() : 0);
            sumCarbs += (food.getCarbs() != null ? food.getCarbs() : 0);
            sumProtein += (food.getProtein() != null ? food.getProtein() : 0);
            sumFat += (food.getFat() != null ? food.getFat() : 0);
        }

        dto.setTotalKcal(sumKcal);
        dto.setTotalCarbs(sumCarbs);
        dto.setTotalProtein(sumProtein);
        dto.setTotalFat(sumFat);
    }

    // ★ 헬퍼: AI JSON 파싱 (리스트 지원)
    private void applyAiResult(DietDto dietDto, String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);
            List<DietDetailDto> foodList = new ArrayList<>();

            // 1. "foodList" 배열 파싱
            if (root.has("foodList") && root.get("foodList").isArray()) {
                for (JsonNode node : root.get("foodList")) {
                    DietDetailDto detail = new DietDetailDto();
                    detail.setFoodName(node.path("foodName").asText("알 수 없음"));
                    detail.setKcal(node.path("kcal").asDouble(0));
                    detail.setCarbs(node.path("carbs").asDouble(0));
                    detail.setProtein(node.path("protein").asDouble(0));
                    detail.setFat(node.path("fat").asDouble(0));
                    detail.setAmount(node.path("amount").asInt(0));
                    foodList.add(detail);
                }
                dietDto.setFoodList(foodList);
            }

            // 2. 전체 코멘트 및 점수
            if (root.has("comment")) {
                dietDto.setAiAnalysis(root.get("comment").asText());
            }
            if (root.has("score")) {
                dietDto.setScore(root.get("score").asInt());
            }

            // 3. (옵션) 메모가 비어있으면 첫 번째 음식 이름으로 채우기
            if ((dietDto.getMemo() == null || dietDto.getMemo().isBlank()) && !foodList.isEmpty()) {
                dietDto.setMemo(foodList.get(0).getFoodName());
            }

        } catch (Exception e) {
            log.error("AI 응답 파싱 실패: {}", jsonResult, e);
        }
    }

    // ... (나머지 단순 조회/삭제 메서드는 기존과 동일 유지) ...
    @Override
    public List<DietDto> getDailyDiets(Long userId, String date) {
        return dietDao.selectDailyDiets(userId, date);
    }

    @Override
    public List<DietDto> getWeeklyDiets(Long userId, String s, String e) {
        return dietDao.selectWeeklyDiets(userId, s, e);
    }

    @Override
    public List<DietDto> getMonthlyDiets(Long userId, String month) {
        return dietDao.selectMonthlyDiets(userId, month);
    }

    @Override
    public List<DietDto> getAllDiets(Long userId) {
        return dietDao.selectAllDiets(userId);
    }

    @Override
    public DietDto getDietDetail(Long dietId) {
        return dietDao.selectDietDetail(dietId);
    }

    @Override
    @Transactional
    public void deleteDiet(Long dietId) {
        dietDao.deleteDiet(dietId);
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

    @Override
    public int getDietStreak(Long userId) {
        // 1. DB에서 기록된 날짜들 가져오기 (이미 내림차순 정렬됨)
        List<String> dates = dietDao.selectDietDates(userId);
        if (dates.isEmpty()) {
            return 0;
        }

        // 검색 속도를 위해 Set으로 변환
        java.util.Set<String> dateSet = new java.util.HashSet<>(dates);

        int streak = 0;
        java.time.LocalDate checkDate = java.time.LocalDate.now(); // 오늘 날짜

        // 2. 오늘 기록이 있는지 확인
        // 만약 오늘 안 썼다면, 어제 기록이 있는지 확인 (어제 썼으면 스트릭 유지 중인 것)
        if (!dateSet.contains(checkDate.toString())) {
            checkDate = checkDate.minusDays(1); // 기준을 어제로 변경
            if (!dateSet.contains(checkDate.toString())) {
                return 0; // 어제도 안 썼으면 스트릭 끊김 (0일)
            }
        }

        // 3. 과거로 가면서 연속 출석 카운트
        while (dateSet.contains(checkDate.toString())) {
            streak++;
            checkDate = checkDate.minusDays(1); // 하루 전으로 이동
        }

        return streak;
    }
}
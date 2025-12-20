package com.ssafy.bapai.diet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.ai.service.AiService;
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

    // 1. 식단 등록 (이미지 + AI + DB저장)
    @Override
    @Transactional
    public void registerDiet(DietDto dietDto, MultipartFile file) {
        // A. 이미지 업로드
        if (file != null && !file.isEmpty()) {
            try {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl);
            } catch (Exception e) {
                log.error("이미지 업로드 실패", e);
                throw new RuntimeException("이미지 업로드 중 오류 발생");
            }
        }

        // B. AI 분석 요청
        String aiResponse = aiService.analyzeFood(file, dietDto.getMemo());

        // C. AI 결과 파싱 (리스트로 변환)
        applyAiResult(dietDto, aiResponse);

        // D. 영양소 총합 계산 (음식 리스트 합계 -> DietDto)
        calculateTotalNutrition(dietDto);

        // E. DB 저장
        saveDiet(dietDto);
    }

    // 2. 순수 DB 저장 (메인 + 상세 한방 저장)
    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        // 1) diet 테이블 저장 (ID 생성됨)
        dietDao.insertDiet(dietDto);

        // 2) diet_detail 테이블 저장 (생성된 ID 연결 후 Bulk Insert)
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
            }
            dietDao.insertDietDetails(dietDto.getFoodList()); // 한방 쿼리 사용
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
    public List<DietDto> getDietList(String sort, int size, int offset) {
        return dietDao.selectDietList(sort, size, offset);
    }

    @Override
    public int getDietCount() {
        return dietDao.selectDietCount();
    }
}
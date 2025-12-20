package com.ssafy.bapai.diet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bapai.ai.service.AiService;
import com.ssafy.bapai.common.s3.S3Service;
import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j // ★ 추가 (로그 사용을 위해 필수)
@Service
@RequiredArgsConstructor
public class DietServiceImpl implements DietService {

    private final DietDao dietDao;
    private final S3Service s3Service;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    // 1. 식단 저장 (메인 + 상세 동시 저장)
    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        // diet 테이블 저장
        dietDao.insertDiet(dietDto);

        // 생성된 dietId를 가지고 diet_detail 테이블 저장
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
                dietDao.insertDietDetail(detail);
            }
        }
    }

    @Override
    public List<DietDto> getDailyDiets(Long userId, String date) {
        return dietDao.selectDailyDiets(userId, date);
    }

    @Override
    public List<DietDto> getWeeklyDiets(Long userId, String startDate, String endDate) {
        return dietDao.selectWeeklyDiets(userId, startDate, endDate);
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

    // 2. 식단 수정
    @Override
    @Transactional
    public void updateDiet(DietDto dietDto) {
        // 메인 정보 수정
        dietDao.updateDiet(dietDto);

        // 기존 상세 내역 싹 지우기
        dietDao.deleteDietDetailsByDietId(dietDto.getDietId());

        //  새로운 상세 내역 다시 넣기
        if (dietDto.getFoodList() != null && !dietDto.getFoodList().isEmpty()) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietDto.getDietId());
                dietDao.insertDietDetail(detail);
            }
        }
    }

    @Override
    @Transactional
    public void deleteDiet(Long dietId) {
        dietDao.deleteDiet(dietId);
    }

    // AI 분석 및 이미지 업로드를 포함한 식단 등록
    @Override
    @Transactional
    public void registerDiet(DietDto dietDto, MultipartFile file) {

        // 1. 이미지 처리 (S3 업로드)
        if (file != null && !file.isEmpty()) {
            try {
                String imgUrl = s3Service.uploadFile(file, "diet");
                dietDto.setDietImg(imgUrl); // DTO에 이미지 URL 저장
            } catch (Exception e) {
                log.error("이미지 업로드 실패", e);
                throw new RuntimeException("이미지 업로드 중 오류 발생");
            }
        }

        // 2. AI 분석 요청 (이미지 유무 상관없이 호출)
        // dietDto.getMemo()에 사용자 입력 음식명이 있다고 가정
        String aiResponse = aiService.analyzeFood(file, dietDto.getMemo());

        // 3. AI 결과 파싱 및 DTO에 적용
        applyAiResult(dietDto, aiResponse);

        // 4. 최종 DB 저장 (기존 saveDiet 로직 재사용)
        saveDiet(dietDto);
    }

    // AI 결과를 DTO에 매핑하는 헬퍼 메서드
    private void applyAiResult(DietDto dietDto, String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);

            // 음식명이 비어있으면 AI가 분석한 이름 사용
            if (dietDto.getMemo() == null || dietDto.getMemo().isBlank()) {
                if (root.has("menuName")) {
                    dietDto.setMemo(root.get("menuName").asText());
                }
            }

            // 영양 정보 적용 (값이 있으면 덮어쓰기)
            if (root.has("calories")) {
                dietDto.setTotalKcal(root.get("calories").asDouble());
            }
            if (root.has("score")) {
                dietDto.setScore(root.get("score").asInt());
            }
            if (root.has("aiAnalysis")) {
                dietDto.setAiAnalysis(root.get("aiAnalysis").asText()); // 분석 코멘트가 있다면
            }

            log.info("AI 분석 적용 완료: {} ({} kcal)", dietDto.getMemo(), dietDto.getTotalKcal());

        } catch (Exception e) {
            log.error("AI 응답 파싱 실패 (무시하고 진행): {}", jsonResult, e);
        }
    }
}
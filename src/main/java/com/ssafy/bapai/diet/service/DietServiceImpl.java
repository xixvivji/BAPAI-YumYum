package com.ssafy.bapai.diet.service;

import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DietServiceImpl implements DietService {

    private final DietDao dietDao;

    // 1. 식단 등록
    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        // (1) 총 칼로리 계산
        double totalKcal = calculateTotalKcal(dietDto.getFoodList());
        dietDto.setTotalKcal(totalKcal);

        // (2) 메인 식단 저장 (diet_id 생성됨)
        dietDao.insertDiet(dietDto);
        Long dietId = dietDto.getDietId();

        // (3) 상세 음식 리스트 저장
        saveDietDetails(dietId, dietDto.getFoodList());
    }

    // 2. 날짜별 조회
    @Override
    public List<DietDto> getDailyDiets(Long userId, String date) {
        List<DietDto> dietList = dietDao.selectDietsByDate(userId, date);

        // N+1 문제 방지 (단순 루프 방식)
        // 각 식단마다 "상세 음식 리스트"를 채워줌
        for (DietDto diet : dietList) {
            List<DietDetailDto> details = dietDao.selectDietDetails(diet.getDietId());
            diet.setFoodList(details);
        }
        return dietList;
    }

    // 3. 삭제
    @Override
    @Transactional
    public void deleteDiet(Long dietId) {
        // DB에 CASCADE가 걸려있다면 detail도 같이 삭제됨
        dietDao.deleteDiet(dietId);
    }

    // 4. [추가] 수정
    @Override
    @Transactional
    public void updateDiet(DietDto dietDto) {
        Long dietId = dietDto.getDietId();

        // (1) 기존 상세 음식 리스트 싹 삭제 (초기화)
        dietDao.deleteDietDetails(dietId);

        // (2) 새 음식 리스트로 총 칼로리 재계산
        double totalKcal = calculateTotalKcal(dietDto.getFoodList());
        dietDto.setTotalKcal(totalKcal);

        // (3) 메인 정보(메모, 사진, 칼로리 등) 업데이트
        dietDao.updateDiet(dietDto);

        // (4) 새 음식 리스트 저장
        saveDietDetails(dietId, dietDto.getFoodList());
    }

    // 5. [추가] 상세 조회
    @Override
    public DietDto getDietDetail(Long dietId) {
        // 메인 정보 조회
        DietDto diet = dietDao.selectDietById(dietId);

        // 상세 음식 리스트 조회해서 합치기
        if (diet != null) {
            List<DietDetailDto> details = dietDao.selectDietDetails(dietId);
            diet.setFoodList(details);
        }
        return diet;
    }

    // --- 내부 헬퍼 메서드 (중복 코드 제거) ---

    // 칼로리 합계 계산
    private double calculateTotalKcal(List<DietDetailDto> foodList) {
        double total = 0;
        if (foodList != null) {
            for (DietDetailDto food : foodList) {
                total += food.getKcal();
            }
        }
        return total;
    }

    // 상세 리스트 저장
    private void saveDietDetails(Long dietId, List<DietDetailDto> foodList) {
        if (foodList != null) {
            for (DietDetailDto detail : foodList) {
                detail.setDietId(dietId); // FK 주입
                dietDao.insertDietDetail(detail);
            }
        }
    }
}
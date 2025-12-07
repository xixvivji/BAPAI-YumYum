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

    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        // 1. 총 칼로리 계산 (서버에서 재계산)
        double totalKcal = 0;
        if (dietDto.getFoodList() != null) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                totalKcal += detail.getKcal();
            }
        }
        dietDto.setTotalKcal(totalKcal);

        // 2. 식단 메인 저장 (PK 생성)
        dietDao.insertDiet(dietDto);
        Long dietId = dietDto.getDietId();

        // 3. 상세 메뉴 저장
        if (dietDto.getFoodList() != null) {
            for (DietDetailDto detail : dietDto.getFoodList()) {
                detail.setDietId(dietId);
                dietDao.insertDietDetail(detail);
            }
        }
    }

    @Override
    public List<DietDto> getDailyDiets(Long userId, String date) {
        List<DietDto> diets = dietDao.selectDietsByDate(userId, date);
        // N+1 문제 방지 로직 (간단하게 루프로 상세 조회)
        for (DietDto diet : diets) {
            diet.setFoodList(dietDao.selectDietDetails(diet.getDietId()));
        }
        return diets;
    }

    @Override
    @Transactional
    public void deleteDiet(Long dietId) {
        dietDao.deleteDiet(dietId);
    }
}
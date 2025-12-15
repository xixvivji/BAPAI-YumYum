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

    // 1. 식단 저장 (메인 + 상세 동시 저장)
    @Override
    @Transactional
    public void saveDiet(DietDto dietDto) {
        // diet 테이블 저장 (PK인 dietId가 생성됨)
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

    // 2. 식단 수정 (기존 상세 삭제 -> 재저장 방식)
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
        // DB의 ON DELETE CASCADE 설정 덕분에 diet만 지워도 detail은 같이 지워짐
        dietDao.deleteDiet(dietId);
    }
}
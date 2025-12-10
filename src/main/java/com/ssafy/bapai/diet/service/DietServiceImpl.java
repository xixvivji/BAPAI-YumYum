package com.ssafy.bapai.diet.service;

import com.ssafy.bapai.diet.dao.DietDao;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DietServiceImpl implements DietService {

    private final DietDao dietDao;

    @Override
    public void saveDiet(DietDto dietDto) {
        dietDao.insertDiet(dietDto);
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

    @Override
    public void updateDiet(DietDto dietDto) {
        dietDao.updateDiet(dietDto);
    }

    @Override
    public void deleteDiet(Long dietId) {
        dietDao.deleteDiet(dietId);
    }
}
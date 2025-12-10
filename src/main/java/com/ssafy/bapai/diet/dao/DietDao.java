package com.ssafy.bapai.diet.dao;

import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DietDao {
    void insertDiet(DietDto dietDto);

    List<DietDto> selectDailyDiets(Long userId, String date);

    List<DietDto> selectWeeklyDiets(Long userId, String startDate, String endDate);

    List<DietDto> selectMonthlyDiets(Long userId, String month);

    List<DietDto> selectAllDiets(Long userId);

    DietDto selectDietDetail(Long dietId);

    void updateDiet(DietDto dietDto);

    void deleteDiet(Long dietId);
}
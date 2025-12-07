package com.ssafy.bapai.diet.dao;

import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DietDao {
    int insertDiet(DietDto dietDto);

    int insertDietDetail(DietDetailDto detailDto);

    List<DietDto> selectDietsByDate(@Param("userId") Long userId, @Param("date") String date);

    List<DietDetailDto> selectDietDetails(Long dietId);

    int deleteDiet(Long dietId);
}
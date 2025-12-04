package com.ssafy.bapai.food.dao;

import com.ssafy.bapai.food.dto.FoodDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FoodDao {
    // 검색 (이름 포함)
    List<FoodDto> searchList(String keyword);

    // 상세 조회
    FoodDto selectFood(String foodCode);
}
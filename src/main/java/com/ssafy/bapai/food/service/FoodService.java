package com.ssafy.bapai.food.service;

import com.ssafy.bapai.food.dto.FoodDto;
import java.util.List;

public interface FoodService {
    List<FoodDto> searchFoods(String keyword);

    FoodDto getFoodDetail(String foodCode);
}
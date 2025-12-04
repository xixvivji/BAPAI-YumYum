package com.ssafy.bapai.food.service;

import com.ssafy.bapai.food.dao.FoodDao;
import com.ssafy.bapai.food.dto.FoodDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FoodServiceImpl implements FoodService {
    private final FoodDao foodDao;

    @Override
    public List<FoodDto> searchFoods(String keyword) {
        return foodDao.searchList(keyword);
    }

    @Override
    public FoodDto getFoodDetail(String foodCode) {
        return foodDao.selectFood(foodCode);
    }
}
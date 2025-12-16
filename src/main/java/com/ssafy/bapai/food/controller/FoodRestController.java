package com.ssafy.bapai.food.controller;

import com.ssafy.bapai.food.dto.FoodDto;
import com.ssafy.bapai.food.service.FoodService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
@CrossOrigin("*")
public class FoodRestController {

    private final FoodService foodService;

    // 1. 음식 검색 API
    @GetMapping
    public ResponseEntity<?> search(@RequestParam String keyword) {
        List<FoodDto> list = foodService.searchFoods(keyword);
        return ResponseEntity.ok(list);
    }

    // 2. 음식 상세 조회 API
    @GetMapping("/{code}")
    public ResponseEntity<?> detail(@PathVariable String code) {
        FoodDto food = foodService.getFoodDetail(code);
        return ResponseEntity.ok(food);
    }
}
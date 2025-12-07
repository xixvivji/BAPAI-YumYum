package com.ssafy.bapai.diet.service;

import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;

public interface DietService {
    void saveDiet(DietDto dietDto);

    List<DietDto> getDailyDiets(Long userId, String date);

    void deleteDiet(Long dietId);
}
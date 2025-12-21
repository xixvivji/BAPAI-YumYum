package com.ssafy.bapai.diet.service;

import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface DietService {
    void saveDiet(DietDto dietDto);

    DietDto analyzeDiet(MultipartFile file, String hint);


    List<DietDto> getDailyDiets(Long userId, String date);

    List<DietDto> getWeeklyDiets(Long userId, String startDate, String endDate);

    List<DietDto> getMonthlyDiets(Long userId, String month);

    List<DietDto> getAllDiets(Long userId);

    DietDto getDietDetail(Long dietId);

    void updateDiet(DietDto dietDto);

    void deleteDiet(Long dietId);

    PageResponse<DietDto> getDietFeed(String sort, int size, int page);

    int getDietCount();

    int getDietStreak(Long userId);
}
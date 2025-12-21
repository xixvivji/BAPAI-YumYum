package com.ssafy.bapai.diet.service;

import com.ssafy.bapai.common.dto.PageResponse;
import com.ssafy.bapai.diet.dto.DailyDietLogDto;
import com.ssafy.bapai.diet.dto.DietDto;
import com.ssafy.bapai.diet.dto.StreakDto;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface DietService {

    // 1. 저장 및 분석
    void saveDiet(DietDto dietDto);

    DietDto analyzeDiet(MultipartFile file, String hint);

    // 2. 조회 (리턴 타입 변경됨)
    // 일간 조회 (통계 + 리스트)
    DailyDietLogDto getDailyDietLog(Long userId, String date);

    // 기간(주간/월간) 조회 (Map 반환)
    Map<String, DailyDietLogDto> getPeriodDietLogs(Long userId, String startDate, String endDate);

    // 전체 리스트 조회 (기존 유지)
    List<DietDto> getAllDiets(Long userId);

    // 상세 조회
    DietDto getDietDetail(Long dietId);

    // 3. 수정 및 삭제
    void updateDiet(DietDto dietDto);

    void deleteDiet(Long dietId);

    // 4. 커뮤니티 피드
    PageResponse<DietDto> getDietFeed(String sort, int size, int page);

    int getDietCount();

    // 5. 스트릭 및 물 관리 (추가/변경됨)
    StreakDto getDietStreak(Long userId); // int -> StreakDto 변경

    void changeWaterCount(Long userId, String date, int delta);

    void changeWaterGoal(Long userId, String date, int delta);
}
package com.ssafy.bapai.diet.service;

import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;

public interface DietService {
    // 1. 등록
    void saveDiet(DietDto dietDto);

    // 2. 날짜별 목록 조회
    List<DietDto> getDailyDiets(Long userId, String date);

    // 3. 삭제
    void deleteDiet(Long dietId);

    // 4. [추가] 수정 (음식 목록 교체 및 칼로리 재계산)
    void updateDiet(DietDto dietDto);

    // 5. [추가] 상세 조회 (단건)
    DietDto getDietDetail(Long dietId);
}
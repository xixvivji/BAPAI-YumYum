package com.ssafy.bapai.diet.dao;

import com.ssafy.bapai.diet.dto.DietDetailDto;
import com.ssafy.bapai.diet.dto.DietDto;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DietDao {
    // 1. 메인 식단(Diet) CRUD
    void insertDiet(DietDto dietDto);

    List<DietDto> selectDailyDiets(@Param("userId") Long userId, @Param("date") String date);

    List<DietDto> selectWeeklyDiets(@Param("userId") Long userId,
                                    @Param("startDate") String startDate,
                                    @Param("endDate") String endDate);

    List<DietDto> selectMonthlyDiets(@Param("userId") Long userId, @Param("month") String month);

    List<DietDto> selectAllDiets(Long userId);

    DietDto selectDietDetail(Long dietId); // 상세 조회 (음식 목록 포함용)

    void updateDiet(DietDto dietDto);

    void deleteDiet(Long dietId);


    //  2. 상세 식단(DietDetail) 관리

    // 상세 음식 1개 저장
    void insertDietDetail(DietDetailDto detailDto);

    List<String> selectDietDates(Long userId);

    // 특정 식단의 음식 목록 조회
    List<DietDetailDto> selectDietDetailsByDietId(Long dietId);

    void insertDietDetails(List<DietDetailDto> details);

    // 식단 수정 시 기존 음식 목록 삭제용
    void deleteDietDetailsByDietId(Long dietId);

    List<DietDto> selectDietList(@Param("sort") String sort,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    //  전체 식단 개수 조회 (페이징용)
    int selectDietCount();


    void updateWaterCountDelta(@Param("userId") Long userId, @Param("date") String date,
                               @Param("delta") int delta);

    void updateWaterGoalDelta(@Param("userId") Long userId, @Param("date") String date,
                              @Param("delta") int delta);

    Map<String, Object> selectWaterInfo(@Param("userId") Long userId, @Param("date") String date);

}
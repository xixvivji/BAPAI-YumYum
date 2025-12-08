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

    // 4. 수정 (메인 정보 업데이트)
    int updateDiet(DietDto dietDto);

    // 5. 상세 메뉴 초기화 (수정 시 기존 메뉴 삭제용)
    int deleteDietDetails(Long dietId);

    // 6. 단건 상세 조회 (메인 정보)
    DietDto selectDietById(Long dietId);
}
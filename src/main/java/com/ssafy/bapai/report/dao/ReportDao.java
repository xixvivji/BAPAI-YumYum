package com.ssafy.bapai.report.dao;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportDao {

    // 1. 나의 기간별 평균 섭취량 조회
    // 리턴타입 Map: { "avgScore": 80.5, "avgCarbs": 200.0 ... }
    Map<String, Double> selectMyStatsByPeriod(@Param("userId") Long userId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    // 2. 팀 랭커(상위 3명)의 기간별 평균 조회
    Map<String, Double> selectRankerStatsByPeriod(@Param("teamId") Long teamId,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);
}
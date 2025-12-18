package com.ssafy.bapai.ai.dao;

import com.ssafy.bapai.ai.dto.ReportLogDto;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportDao {
    // 기존 메서드들
    void insertReportLog(ReportLogDto reportLogDto);

    ReportLogDto selectExistingReport(@Param("userId") Long userId,
                                      @Param("reportType") String reportType,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);

    // 통계 쿼리
    Map<String, Object> selectMyStats(@Param("userId") Long userId,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);

    Map<String, Object> selectRankerStats(@Param("groupId") Long groupId,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate);
}
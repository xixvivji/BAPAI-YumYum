package com.ssafy.bapai.report.dao;

import com.ssafy.bapai.report.dto.ReportLogDto;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportDao {

    Map<String, Double> selectMyStatsByPeriod(@Param("userId") Long userId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    // teamId -> groupId
    Map<String, Double> selectRankerStatsByPeriod(@Param("groupId") Long groupId,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);

    void insertReportLog(ReportLogDto dto);
}
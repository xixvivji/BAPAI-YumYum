package com.ssafy.bapai.ai.dao;

import com.ssafy.bapai.ai.dto.ReportLogDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportDao {
    // 리포트 저장
    void insertReportLog(ReportLogDto reportLogDto);

    // 이미 생성된 리포트가 있는지 조회 (캐싱 체크)
    ReportLogDto selectExistingReport(@Param("userId") Long userId,
                                      @Param("reportType") String reportType,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);
}
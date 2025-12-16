package com.ssafy.bapai.ai.dto;

import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportLogDto {
    private Long reportId;      // PK
    private Long userId;
    private String reportType;  // DAILY, WEEKLY, MONTHLY
    private String startDate;   // YYYY-MM-DD
    private String endDate;     // YYYY-MM-DD
    private Double scoreAverage;
    private String aiMessage;   // AI가 분석한 텍스트
    private Timestamp createdAt;
}
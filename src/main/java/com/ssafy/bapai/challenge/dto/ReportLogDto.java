package com.ssafy.bapai.challenge.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ReportLogDto {
    private Long reportId;
    private Long userId;
    private String reportType; // DAILY, WEEKLY
    private LocalDate startDate;
    private LocalDate endDate;
    private double scoreAverage;
    private String aiMessage;
}
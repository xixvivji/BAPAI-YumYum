package com.ssafy.bapai.common.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> list;      // 데이터 목록
    private int page;          // 현재 페이지
    private int size;          // 페이지 크기
    private int totalPages;    // 전체 페이지 수
    private long totalElements;// 전체 개수
}
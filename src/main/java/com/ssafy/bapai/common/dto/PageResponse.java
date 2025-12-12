package com.ssafy.bapai.common.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PageResponse<T> {

    private List<T> list;      // 실제 데이터 목록 (게시글 리스트 등)
    private int page;             // 현재 페이지 번호
    private int size;             // 한 페이지당 데이터 개수
    private long totalElements;   // 전체 데이터 개수 (DB count 결과)
    private int totalPages;       // 전체 페이지 수 (계산됨)
    private boolean hasNext;      // 다음 페이지가 있는지 여부 (계산됨)

    // ★ 서비스에서 호출하는 생성자 (4개만 넘겨주면 나머지는 알아서 계산)
    public PageResponse(List<T> list, int page, int size, long totalElements) {
        this.list = list;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;

        // 1. 전체 페이지 수 계산 (나눗셈 올림 처리)
        // 예: 글이 13개고 사이즈가 10이면 -> 1.3 -> 올림해서 2페이지가 됨
        if (size > 0) {
            this.totalPages = (int) Math.ceil((double) totalElements / size);
        } else {
            this.totalPages = 0;
        }

        // 2. 다음 페이지 여부 계산
        // 현재 페이지가 전체 페이지 수보다 작으면 다음 페이지가 있음
        this.hasNext = page < totalPages;
    }
}
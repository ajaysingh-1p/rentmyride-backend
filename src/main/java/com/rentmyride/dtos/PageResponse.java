package com.rentmyride.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A small, consistent shape for every paginated list endpoint (issue #25 — previously nothing in
 * the project was paginated; every "get all" endpoint just returned the whole table). Frontend
 * tables can page through {@code content} using {@code page}/{@code totalPages} without the
 * backend ever loading more than one page of rows into memory or sending it over the wire.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private List<T> content;
    private int page;          // current page, 0-indexed
    private int size;          // requested page size
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}

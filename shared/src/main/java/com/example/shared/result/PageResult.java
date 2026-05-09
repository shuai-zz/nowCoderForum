package com.example.shared.result;

import java.util.List;

/**
 * 统一分页响应。基于 MyBatis Plus IPage 构建。
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int pageNum,
        int pageSize,
        int pages
) {

    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        int pages = pageSize == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(list, total, pageNum, pageSize, pages);
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(List.of(), 0L, pageNum, pageSize, 0);
    }
}
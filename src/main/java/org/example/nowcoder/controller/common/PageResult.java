package org.example.nowcoder.controller.common;

import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * 统一分页响应。适配 PageHelper（过渡期）和 MyBatis Plus IPage（P2 后）。
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int pageNum,
        int pageSize,
        int pages
) {

    public static <T> PageResult<T> of(PageInfo<T> p) {
        return new PageResult<>(
                p.getList(),
                p.getTotal(),
                p.getPageNum(),
                p.getPageSize(),
                p.getPages()
        );
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(List.of(), 0L, pageNum, pageSize, 0);
    }
}
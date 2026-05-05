package com.example.shared.result;

import java.util.List;

/**
 * 应用层/领域层通用分页数据容器。
 * <p>仅携带数据列表和总记录数，由 Controller 负责转换为接口层的 {@link PageResult}。
 *
 * @param <T> 列表元素类型
 */
public record PageData<T>(List<T> items, long total) {
}

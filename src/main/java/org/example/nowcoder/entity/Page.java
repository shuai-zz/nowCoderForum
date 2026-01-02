package org.example.nowcoder.entity;

import lombok.Data;
import lombok.Setter;

/**
 * @author 23211
 */
@Data
public class Page {
    // 当前页
    private int pageNum = 1;
    // 每页帖子数
    private int pageSize = 10;
    // 帖子总数
    private long total;
    // 查询路径
    private String path;
}

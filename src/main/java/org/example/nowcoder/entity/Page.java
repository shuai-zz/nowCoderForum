package org.example.nowcoder.entity;

import lombok.Data;
import lombok.Setter;

/**
 * @author 23211
 */
@Data
public class Page {
    // 当前页 current
    private int pageNum = 1;
    // 每页帖子数 limit
    private int pageSize = 10;
    // 帖子总数 rows
    private long total;
    // 总页数
    private int pages;
    // 页码数组
    private int[] navigatepageNums;
    // 查询路径
    private String path;
}

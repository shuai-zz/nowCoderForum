package org.example.nowcoder.entity;

import lombok.Data;

/**
 * @author 23211
 */
@Data
public class Page {
    private int current = 1;
    private int limit = 10;
    private int rows;
    private String path;
}

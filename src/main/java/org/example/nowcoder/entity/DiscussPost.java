package org.example.nowcoder.entity;

import lombok.Data;

/**
 * @author 23211
 */
@Data
public class DiscussPost {
    private int id;
    private int userId;
    private String title;
    private String content;
    private int type;
    private int status;
    private int createTime;
    private int commentCount;
    private double score;
}

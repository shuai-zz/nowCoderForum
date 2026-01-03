package org.example.nowcoder.entity;

import lombok.Data;

import java.util.Date;

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
    private Date createTime;
    private int commentCount;
    private double score;
}

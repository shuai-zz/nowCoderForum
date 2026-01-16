package org.example.nowcoder.entity;

import lombok.Data;

import java.util.Date;

/**
 * @author zhaoshuai
 */
@Data
public class Comment {
    private int id;
    private int userId;
    // 目标种类：帖子，评论
    private int entityType;
    private int entityId;
    private int targetId;
    private String content;
    private int status;
    private Date createTime;
}

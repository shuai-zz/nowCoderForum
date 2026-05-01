package com.example.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author zhaoshuai
 */
@Data
@TableName("comment")
@Builder
public class Comment {

    @TableId(type = IdType.AUTO)
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
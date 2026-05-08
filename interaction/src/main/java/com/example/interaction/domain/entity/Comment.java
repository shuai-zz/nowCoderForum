package com.example.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.shared.constant.ForumConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author zhaoshuai
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private int id;

    private int userId;

    // 目标种类：帖子，评论
    private int entityType;
    // 目标种类id
    private int entityId;
    // 目标种类的作者id
    private int targetId;

    private String content;

    /** 点赞数：事件驱动维护，由 CommentLikeEventListener 监听 EntityLikedEvent 后增减。 */
    private int likeCount;

    private int status;

    private Date createTime;

    public boolean isOnPost() {
        return entityType == ForumConstant.ENTITY_TYPE_POST;
    }

    public boolean isReply() {
        return entityType == ForumConstant.ENTITY_TYPE_COMMENT;
    }

}

package com.example.post.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.shared.exception.ValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.Date;

/**
 * 帖子领域实体（仅 MySQL 持久化）。
 * <p>ES 索引视图见 {@code com.example.search.domain.SearchablePost}，
 * 由 search 模块负责双向投影，避免 domain 实体被 ES 注解污染。
 *
 * @author 23211
 */
@TableName("discuss_post")
@Builder
@Getter
public class DiscussPost {
    @TableId(type = IdType.AUTO)
    private int id;
    private int userId;
    private String title;
    private String content;
    private int type;
    private int status;
    private Date createTime;
    private int commentCount;
    private int likeCount;
    private double score;

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_TOP = 1;
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_WONDERFUL = 1;
    public static final int STATUS_DELETED = 2;



    public void markAsTop(){
        this.type = TYPE_TOP;
    }
    public void markAsWonderful(){
        this.status = STATUS_WONDERFUL;
    }
    public void softDelete(){
        this.status = STATUS_DELETED;
    }
    public boolean isDeleted(){
        return status == STATUS_DELETED;
    }
    public boolean isTop(){
        return type == TYPE_TOP;
    }
    public boolean isWonderful(){
        return status == STATUS_WONDERFUL;
    }
    public void refreshCommentCount(int count){
        if(count < 0){
            throw new ValidationException("comment count cannot be negative");
        }
        this.commentCount = count;
    }
    public void updateScore(double score){
        if(score < 0){
            throw new ValidationException("score cannot be negative");
        }
        this.score = score;
    }
}

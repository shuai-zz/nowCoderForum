package com.example.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author 23211
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private int id;
    private int fromId;
    private int toId;
    private String conversationId;
    private String content;
    /**
     * 0-未读;
     * 1-已读;
     * 2-删除
     */
    private int status;
    private Date createTime;

    /**
     * 写入持久化前替换为净化后的正文。
     * 由 application service 在调用 ContentSanitizer 后回填，避免 service 用 builder 重建整个实体。
     */
    public void applySanitizedContent(String sanitizedContent) {
        this.content = sanitizedContent;
    }
}

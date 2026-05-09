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
}

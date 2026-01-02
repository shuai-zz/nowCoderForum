package org.example.nowcoder.entity;

import lombok.Data;

/**
 * @author 23211
 */
@Data
public class Message {
    private int id;
    private int fromId;
    private int toId;
    private String conversationId;
    private String content;
    private int status;
    private String createTime;
}

package org.example.nowcoder.entity.vo;

import java.util.Date;

/**
 * 通知列表项。
 *
 * @param from       触发此通知的用户（从 content.userId 解析出来的）
 * @param status     0=未读, 1=已读
 */
public record NoticeVO(
        int id,
        String topic,
        UserVO from,
        Integer entityType,
        Integer entityId,
        Integer postId,
        int status,
        Date createTime
) {}

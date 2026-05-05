package com.example.message.interfaces.vo;

import com.example.user.interfaces.vo.UserVO;

import java.util.Date;

/**
 * 通知摘要（某个 topic 的最新一条通知 + 计数），用于 /notices overview。
 *
 * @param hasNotice        该 topic 下是否有通知（false 时其他字段可为空）
 * @param lastFrom         触发最新通知的用户（通知 content.userId 对应）
 * @param entityType       最新通知涉及的实体类型
 * @param entityId         最新通知涉及的实体 id
 * @param postId           最新通知涉及的所属帖子 id（点赞/评论回复等可能有）
 * @param count            该 topic 下通知总数
 * @param unread           该 topic 下未读数
 * @param lastCreateTime   最新通知时间
 */
public record NoticeSummaryVO(
        String topic,
        boolean hasNotice,
        UserVO lastFrom,
        Integer entityType,
        Integer entityId,
        Integer postId,
        long count,
        long unread,
        Date lastCreateTime
) {
    public static NoticeSummaryVO empty(String topic) {
        return new NoticeSummaryVO(topic, false, null, null, null, null, 0L, 0L, null);
    }
}

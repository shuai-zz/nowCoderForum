package com.example.message.interfaces.vo;

/**
 * 通知中心总览：三个主要 topic 的摘要 + 私信与通知的总未读计数。
 */
public record NoticeOverviewVO(
        NoticeSummaryVO commentNotice,
        NoticeSummaryVO likeNotice,
        NoticeSummaryVO followNotice,
        int letterUnreadCount,
        int noticeUnreadCount
) {}

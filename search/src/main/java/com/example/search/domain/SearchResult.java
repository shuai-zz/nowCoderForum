package com.example.search.domain;

import com.example.shared.dto.AuthorRef;

import java.util.Date;

/**
 * 搜索结果值对象（domain 层）。
 * 高亮字段独立存储，不污染原始 title/content。
 * Repository 返回时 author 为 null，由 Service 层填充。
 */
public record SearchResult(
        int id,
        int userId,
        AuthorRef author,
        String title,
        String content,
        String highlightTitle,
        String highlightContent,
        int type,
        int status,
        int commentCount,
        long likeCount,
        double score,
        Date createTime
) {
    public static SearchResult of(
            int id,
            int userId,
            AuthorRef author,
            String title,
            String content,
            String highlightTitle,
            String highlightContent,
            int type,
            int status,
            int commentCount,
            long likeCount,
            double score,
            Date createTime
    ) {
        return new SearchResult(id, userId, author, title, content, highlightTitle, highlightContent, type, status, commentCount, likeCount, score, createTime);
    }
}

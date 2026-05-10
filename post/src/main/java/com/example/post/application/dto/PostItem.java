package com.example.post.application.dto;


import com.example.post.domain.entity.DiscussPost;
import com.example.shared.dto.AuthorRef;

import java.util.Date;

/**
 * 帖子展示项（application 层 DTO）。
 * <p>不再持有 {@link DiscussPost} domain entity —— 把面向展示用的字段拍平，避免 interfaces 层
 * 直接消费 domain entity，保留 application/domain 边界。
 *
 * @param likeStatus 当前用户点赞状态；列表场景固定传 0
 */
public record PostItem(
        Integer id,
        String title,
        String content,
        int type,
        int status,
        int commentCount,
        double score,
        Date createTime,
        AuthorRef author,
        long likeCount,
        int likeStatus
) {

    public static PostItem from(DiscussPost post, AuthorRef author, long likeCount, int likeStatus) {
        if (post == null) {
            return missing(author);
        }
        return new PostItem(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getType(),
                post.getStatus(),
                post.getCommentCount(),
                post.getScore(),
                post.getCreateTime(),
                author,
                likeCount,
                likeStatus
        );
    }

    /** 帖子不存在 / 已删时的占位实例：id 为 null 让 controller 短路成 404。 */
    public static PostItem missing(AuthorRef author) {
        return new PostItem(null, null, null, 0, 0, 0, 0d, null, author, 0L, 0);
    }

    public boolean isMissing() {
        return id == null;
    }
}

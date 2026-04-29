package org.example.nowcoder.entity.vo;

import org.example.nowcoder.entity.Comment;

import java.util.Date;

/**
 * 评论下的回复。target 为被 @ 的用户（若有）。
 */
public record ReplyVO(
        int id,
        String content,
        UserVO author,
        UserVO target,
        long likeCount,
        int likeStatus,
        Date createTime
) {
    public static ReplyVO of(Comment c, UserVO author, UserVO target, long likeCount, int likeStatus) {
        return new ReplyVO(
                c.getId(),
                c.getContent(),
                author,
                target,
                likeCount,
                likeStatus,
                c.getCreateTime()
        );
    }
}

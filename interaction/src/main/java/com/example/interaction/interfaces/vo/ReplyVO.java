package com.example.interaction.interfaces.vo;

import com.example.interaction.domain.entity.Comment;
import com.example.user.interfaces.vo.UserVO;

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
    public static ReplyVO of(Comment r, UserVO author, UserVO target, long likeCount, int likeStatus) {
        return new ReplyVO(
                r.getId(),
                r.getContent(),
                author,
                target,
                likeCount,
                likeStatus,
                r.getCreateTime()
        );
    }
}

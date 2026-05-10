package com.example.post.interfaces.vo;


import com.example.post.application.dto.PostItem;
import com.example.user.interfaces.vo.UserVO;

import java.util.Date;

/**
 * 帖子详情。
 *
 * @param likeStatus 0=未点赞，1=已点赞；未登录时固定 0
 */
public record PostDetailVO(
        int id,
        String title,
        String content,
        UserVO author,
        long likeCount,
        int commentCount,
        int type,
        int status,
        Date createTime,
        double score
) {
    public static PostDetailVO of(PostItem item, UserVO author) {
        return new PostDetailVO(
                item.id(),
                item.title(),
                item.content(),
                author,
                item.likeCount(),
                item.commentCount(),
                item.type(),
                item.status(),
                item.createTime(),
                item.score()
        );
    }
}

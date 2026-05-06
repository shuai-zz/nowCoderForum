package com.example.post.interfaces.vo;


import com.example.post.domain.entity.DiscussPost;
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
    public static PostDetailVO of(DiscussPost post, UserVO author, long likeCount) {
        return new PostDetailVO(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                author,
                likeCount,
                post.getCommentCount(),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getScore()
        );
    }
}

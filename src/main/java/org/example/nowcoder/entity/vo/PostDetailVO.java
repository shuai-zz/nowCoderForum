package org.example.nowcoder.entity.vo;

import org.example.nowcoder.entity.DiscussPost;

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
        int likeStatus,
        int commentCount,
        int type,
        int status,
        Date createTime,
        double score
) {
    public static PostDetailVO of(DiscussPost post, UserVO author, long likeCount, int likeStatus) {
        return new PostDetailVO(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                author,
                likeCount,
                likeStatus,
                post.getCommentCount(),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getScore()
        );
    }
}

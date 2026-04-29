package org.example.nowcoder.entity.vo;

import org.example.nowcoder.entity.DiscussPost;

import java.util.Date;

/**
 * 帖子列表项。content 截断为 excerpt，减小列表接口负载。
 */
public record PostListItemVO(
        int id,
        String title,
        String contentExcerpt,
        UserVO author,
        int commentCount,
        long likeCount,
        int type,
        int status,
        Date createTime,
        double score
) {
    private static final int EXCERPT_LENGTH = 150;

    public static PostListItemVO of(DiscussPost post, UserVO author, long likeCount) {
        return new PostListItemVO(
                post.getId(),
                post.getTitle(),
                excerpt(post.getContent()),
                author,
                post.getCommentCount(),
                likeCount,
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getScore()
        );
    }

    private static String excerpt(String content) {
        if (content == null) return "";
        return content.length() <= EXCERPT_LENGTH
                ? content
                : content.substring(0, EXCERPT_LENGTH) + "...";
    }
}

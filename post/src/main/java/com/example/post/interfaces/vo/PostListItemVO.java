package com.example.post.interfaces.vo;

import com.example.post.application.dto.PostItem;
import com.example.user.interfaces.vo.UserVO;

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

    public static PostListItemVO of(PostItem item, UserVO author) {
        return new PostListItemVO(
                item.id(),
                item.title(),
                excerpt(item.content()),
                author,
                item.commentCount(),
                item.likeCount(),
                item.type(),
                item.status(),
                item.createTime(),
                item.score()
        );
    }

    public static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= EXCERPT_LENGTH
                ? content
                : content.substring(0, EXCERPT_LENGTH) + "...";
    }
}

package com.example.post.application.dto;


import com.example.post.domain.entity.DiscussPost;
import com.example.user.domain.User;

/**
 * @param likeStatus 当前用户点赞状态；列表场景固定传 0
 */
public record PostItem(DiscussPost discussPost, User author, long likeCount, int likeStatus) {
    public static PostItem of(DiscussPost discussPost, User author, long likeCount, int likeStatus) {
        return new PostItem(discussPost, author, likeCount, likeStatus);
    }
}

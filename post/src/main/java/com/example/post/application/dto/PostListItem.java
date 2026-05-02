package com.example.post.application.dto;


import com.example.post.domain.entity.DiscussPost;
import com.example.user.domain.User;


public record PostListItem(DiscussPost discussPost, User author, long likeCount) {
}

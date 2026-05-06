package com.example.interaction.interfaces.vo;

import com.example.interaction.domain.entity.Comment;
import com.example.user.interfaces.vo.UserVO;
import com.example.interaction.interfaces.vo.ReplyVO;

import java.util.Date;
import java.util.List;

/**
 * 帖子的一级评论，内嵌一层回复列表（回复的回复不再嵌套）。
 */
public record CommentVO(
        int id,
        String content,
        UserVO author,
        long likeCount,
        int likeStatus,
        Date createTime,
        List<ReplyVO> replies,
        long replyCount
) {
    public static CommentVO of(Comment c, UserVO author, long likeCount, int likeStatus,
                               List<ReplyVO> replies, long replyCount) {
        return new CommentVO(
                c.getId(),
                c.getContent(),
                author,
                likeCount,
                likeStatus,
                c.getCreateTime(),
                replies,
                replyCount
        );
    }
}

package com.example.interaction.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.interaction.domain.entity.Comment;
import com.example.shared.common.result.PageData;

/**
 * @author zhaoshuai
 */
public interface CommentService extends IService<Comment> {

    PageData<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize);

    int findCommentCount(int entityType, int entityId);

    int addComment(Comment comment);

    Comment findCommentById(int id);

}
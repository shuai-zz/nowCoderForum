package org.example.nowcoder.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Comment;
import org.example.nowcoder.mapper.CommentMapper;
import org.example.nowcoder.service.CommentService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private final CommentMapper commentMapper;

    @Override
    public PageInfo<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize){
        PageHelper.startPage(pageNum,pageSize);
        List<Comment> list=commentMapper.selectCommentsByEntity(entityType,entityId);
        return new PageInfo<>(list);
    }

    @Override
    public int findCommentCount(int entityType, int entityId) {
        return commentMapper.selectCountByEntity(entityType,entityId);
    }
}

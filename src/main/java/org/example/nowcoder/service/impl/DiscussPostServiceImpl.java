package org.example.nowcoder.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.mapper.DiscussPostMapper;
import org.example.nowcoder.service.DiscussPostService;
import org.example.nowcoder.utils.SensitiveFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * @author 23211
 */
@Service
@RequiredArgsConstructor
public class DiscussPostServiceImpl implements DiscussPostService {
    private final DiscussPostMapper discussPostMapper;
    private final SensitiveFilter sensitiveFilter;
    @Override
    public PageInfo<DiscussPost> selectDiscussPosts(int userId, int pageNum, int pageSize) {
        // 该方法会拦截第一个MyBatis查询
        PageHelper.startPage(pageNum,pageSize);
        // 自动转换为分页查询
        List<DiscussPost> list=discussPostMapper.selectDiscussPosts(userId);
        // 自动计算总页数、所有帖子等信息
        return new PageInfo<>(list);
    }

    @Override
    public int insertDiscussPost(DiscussPost discussPost) {
        if(discussPost==null) {
            throw new IllegalArgumentException("post cannot be null");
        }

        // 转义HTML标记
        discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
        discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));

        // 过滤敏感词
        discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
        discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));


        return discussPostMapper.insertDiscussPost(discussPost);
    }
}

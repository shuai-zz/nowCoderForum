package org.example.nowcoder.service;

import com.github.pagehelper.PageInfo;
import org.example.nowcoder.entity.DiscussPost;


/**
 * @author 23211
 */
public interface DiscussPostService {
    PageInfo<DiscussPost> selectDiscussPosts(int userId, int pageNum, int pageSize);
    int insertDiscussPost(DiscussPost discussPost);
}

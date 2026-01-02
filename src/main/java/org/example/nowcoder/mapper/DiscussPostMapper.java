package org.example.nowcoder.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.nowcoder.entity.DiscussPost;

import java.util.List;

/**
 * @author 23211
 */
@Mapper
public interface DiscussPostMapper {
    List<DiscussPost> selectDiscussPosts(int userId, int offset, int limit);
    int selectDiscussPostRows(@Param("userId") int userId);
    int insertDiscussPost(DiscussPost discussPost);
    DiscussPost selectDiscussPostById(int id);
    int updateCommentCount(int id, int commentCount);

}

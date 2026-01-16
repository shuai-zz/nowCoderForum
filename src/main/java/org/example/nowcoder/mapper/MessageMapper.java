package org.example.nowcoder.mapper;

import com.github.pagehelper.PageInfo;
import org.apache.ibatis.annotations.Mapper;
import org.example.nowcoder.entity.Message;

import java.util.List;

/**
 * @author 23211
 */
@Mapper
public interface MessageMapper {
    // 查询当前用户的会话列表，针对每个会话只返回一条最新的消息 TODO 分页
    List<Message> selectConversations(int userId);
    // 查询当前用户的会话数量
    int selectConversationCount(int userId);
    // 查询某会话的私信列表 TODO 分页
    List<Message> selectDms(String conversationId);
    // 查询某个会话的私信数量
    int selectDmCount(String conversationId);
    // 查询未读消息数量
    int selectUnreadCount(int userId, String conversationId);
//    int insertMessage(Message message);
//    int updateStatus(List<Integer> ids, int status);

}

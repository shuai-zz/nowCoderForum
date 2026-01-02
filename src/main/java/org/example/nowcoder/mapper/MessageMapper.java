package org.example.nowcoder.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.nowcoder.entity.Message;

import java.util.List;

/**
 * @author 23211
 */
@Mapper
public interface MessageMapper {
    List<Message> selectConversations(int userId, int offset, int limit);
    int selectConversationCount(int userId);
//    List<Message> selectDMs(String conversationId, int offset, int limit);
//    int selectDMCount(String conversationId);
//    int selectUnreadCount(int userId, String conversationId);
//    int insertMessage(Message message);
//    int updateStatus(List<Integer> ids, int status);

}

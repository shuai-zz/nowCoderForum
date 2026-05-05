package com.example.message.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.message.domain.entity.Message;
import com.example.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 23211
 */
@SuppressWarnings("MybatisPlusMapperMethodInspection")
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    int SYSTEM_NOTICE = 1;
    int UNREAD_MESSAGE = 0;
    int READ_MESSAGE = 1;
    int DELETE_MESSAGE = 2;

    // 分页查询当前用户的会话列表，针对每个会话只返回一条最新的消息
    List<Message> selectConversations(IPage<Message> page, int userId);

    // 查询当前用户的会话数量
    int selectConversationCount(int userId);

    // 查询某会话的私信列表
    default List<Message> selectDms(IPage<Message> page, String conversationId) {
        return selectList(page, Wrappers.<Message>lambdaQuery()
                .ne(Message::getStatus, DELETE_MESSAGE)
                .ne(Message::getFromId, SYSTEM_NOTICE)
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getCreateTime)
        );
    }

    // 查询某个会话的私信数量
    default int selectDmCount(String conversationId) {
        return Math.toIntExact(selectCount(Wrappers.<Message>lambdaQuery()
                .ne(Message::getStatus, DELETE_MESSAGE)
                .ne(Message::getFromId, SYSTEM_NOTICE)
                .eq(Message::getConversationId, conversationId)
        ));
    }

    // 查询未读消息数量
    int selectUnreadCount(int userId, String conversationId);

    // 新增消息
    default int insertMessage(Message message) {
        return insert(message);
    }

    // 批量修改消息状态
    int updateStatus(List<Integer> ids, int status);

    // 查询某个主题下最新的通知
    Message selectLatestNotice(int userId, String topic);

    // 查询某个主题所包含的通知数量
    default int selectNoticeCount(int userId, String topic) {
        return Math.toIntExact(selectCount(Wrappers.<Message>lambdaQuery()
                .ne(Message::getStatus, DELETE_MESSAGE)
                .eq(Message::getFromId, SYSTEM_NOTICE)
                .eq(Message::getToId, userId)
                .eq(Message::getConversationId, topic)
        ));
    }

    // 查询未读的通知数量
    int selectNoticeUnreadCount(int userId, String topic);

    // 查询某个主题所包含的通知列表
    default List<Message> selectNotices(IPage<Message> page, int userId, String topic) {
        return selectList(page, Wrappers.<Message>lambdaQuery()
                .ne(Message::getStatus, DELETE_MESSAGE)
                .eq(Message::getFromId, SYSTEM_NOTICE)
                .eq(Message::getToId, userId)
                .eq(Message::getConversationId, topic)
                .orderByDesc(Message::getCreateTime)
        );
    }



    // 批量查询conversations中的消息数量
    List<Map<String, Object>> selectDmCounts(List<String> conversationIds);


    List<Map<String, Object>> selectUnreadDmCounts(@Param("currUserId") int currUserid, @Param("conversationIds") List<String> conversationIds);

}

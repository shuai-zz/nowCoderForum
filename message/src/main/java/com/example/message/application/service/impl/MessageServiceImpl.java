package com.example.message.application.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.message.application.dto.MessageItem;
import com.example.message.application.service.MessageService;
import com.example.message.domain.entity.Message;
import com.example.message.infrastructure.mapper.MessageMapper;
import com.example.shared.domain.ContentSanitizer;
import com.example.shared.dto.AuthorRef;
import com.example.shared.result.PageData;
import com.example.user.application.service.UserService;
import com.example.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageMapper messageMapper;
    private final UserService userService;
    private final ContentSanitizer contentSanitizer;

    @Override
    public PageData<MessageItem> findConversations(int userId, int pageNum, int pageSize) {
        Page<Message> page = new Page<>(pageNum,pageSize);
        List<Message> conversations = messageMapper.selectConversations(page, userId);
        List<Integer> targetIds = conversations.stream()
                .map(conv -> userId == conv.getFromId() ? conv.getToId() : conv.getFromId())
                .toList();
        List<String> conversationIds = conversations.stream()
                .map(Message::getConversationId)
                .toList();
        Map<Integer, User> userMap = userService.listByIds(targetIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, Integer> dmCountMap = findDmCounts(conversationIds);
        Map<String, Integer> unreadMap = findUnreadDmCounts(userId, conversationIds);
        List<MessageItem> list = conversations.stream()
                .map(conv -> {
                    // 消息发送者
                    AuthorRef from = toAuthorRef(userMap.get(conv.getFromId()));

                    // 消息接收者
                    int targetId = userId == conv.getFromId() ? conv.getToId() : conv.getFromId();
                    AuthorRef to = toAuthorRef(userMap.get(targetId));

                    String conversationId = conv.getConversationId();
                    String content = conv.getContent();
                    Date createTime = conv.getCreateTime();
                    int status = conv.getStatus();
                    Integer dmCount = dmCountMap.getOrDefault(conversationId, 0);
                    Integer unreadDmCount = unreadMap.getOrDefault(conversationId, 0);
                    return MessageItem.of(
                            conv.getId(),
                            from,
                            to,
                            conversationId,
                            content,
                            createTime,
                            status,
                            dmCount,
                            unreadDmCount
                    );
                }).toList();


        return new PageData<>(list, page.getTotal());
    }

    @Override
    public int findConversationCount(int userId) {
        return messageMapper.selectConversationCount(userId);
    }

    @Override
    public PageData<MessageItem> findDms(String conversationId, int pageNum, int pageSize) {
        Page<Message> page = new Page<>(pageNum,pageSize);
        List<Message> messages = messageMapper.selectDms(page, conversationId);

        // 批量获取发送/接收用户
        List<Integer> userIds = messages.stream()
                .flatMap(message -> Stream.of(message.getFromId(), message.getToId()))
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<MessageItem> list = messages.stream()
                .map(message -> {
                    // 消息发送者
                    AuthorRef from = toAuthorRef(userMap.get(message.getFromId()));

                    // 消息接收者
                    AuthorRef to = toAuthorRef(userMap.get(message.getToId()));

                    return MessageItem.of(
                            message.getId(),
                            from,
                            to,
                            message.getConversationId(),
                            message.getContent(),
                            message.getCreateTime(),
                            message.getStatus(),
                            0,
                            0
                    );
                }).toList();
        return new PageData<>(list, page.getTotal());

    }

    @Override
    public int findDmCount(String conversationId) {
        return messageMapper.selectDmCount(conversationId);
    }

    @Override
    public Map<String, Integer> findDmCounts(List<String> conversationIds) {
        if(conversationIds == null|| conversationIds.isEmpty()){
            return Map.of();
        }
        List<Map<String, Object>> list = messageMapper.selectDmCounts(conversationIds);
        Map<String, Integer> result=new HashMap<>();
        for (var map : list) {
            result.put((String) map.get("conversationId"), ((Number)map.get("cnt")).intValue());
        }
        return result;
    }

    @Override
    public int findUnreadCount(int userId, String conversationId) {
        return messageMapper.selectUnreadCount(userId, conversationId);
    }

    @Override
    public Map<String, Integer> findUnreadDmCounts(int currUserid, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> list = messageMapper.selectUnreadDmCounts(currUserid, conversationIds);
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> map : list) {
            result.put((String) map.get("conversationId"), ((Number) map.get("cnt")).intValue());
        }
        return result;
    }


    @Override
    public int addMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("parameter cannot be null");
        }
        message.applySanitizedContent(contentSanitizer.sanitize(message.getContent()));
        return messageMapper.insertMessage(message);
    }

    @Override
    public int updateStatus(List<Integer> ids, int status) {
        return messageMapper.updateStatus(ids, status);
    }

    @Override
    public Message findLatestNotice(int userId, String topic) {
        return messageMapper.selectLatestNotice(userId, topic);
    }

    @Override
    public int findNoticeCount(int userId, String topic) {
        return messageMapper.selectNoticeCount(userId, topic);
    }

    @Override
    public int findNoticeUnreadCount(int userId, String topic) {
        return messageMapper.selectNoticeUnreadCount(userId, topic);
    }

    @Override
    public PageData<MessageItem> findNotices(int userId, String topic, int pageNum, int pageSize) {
        Page<Message> page = new Page<>(pageNum,pageSize);
        List<Message> notices = messageMapper.selectNotices(page, userId, topic);
        // 批量获取发送/接收用户
        List<Integer> userIds = notices.stream()
                .flatMap(notice -> Stream.of(notice.getFromId(), notice.getToId()))
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<MessageItem> list = notices.stream()
                .map(notice -> {
                    // 消息发送者
                    AuthorRef from = toAuthorRef(userMap.get(notice.getFromId()));
                    // 消息接收者
                    AuthorRef to = toAuthorRef(userMap.get(notice.getToId()));

                    return MessageItem.of(notice.getId(),
                            from,
                            to,
                            notice.getConversationId(),
                            notice.getContent(),
                            notice.getCreateTime(),
                            notice.getStatus(),
                            0,
                            0
                    );
                }).toList();
        return new PageData<>(list, page.getTotal());
    }

    /**
     * 把 user 转成 AuthorRef，user 已被删除（null）时返回占位 ref。
     * 内容性数据（私信、通知）即使作者删了也要继续展示给对端。
     */
    private static AuthorRef toAuthorRef(User user) {
        return user == null
                ? AuthorRef.deleted()
                : AuthorRef.of(user.getId(), user.getUsername(), user.getAvatarUrl());
    }
}

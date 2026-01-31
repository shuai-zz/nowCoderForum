package org.example.nowcoder.controller;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.github.pagehelper.PageInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.Host;
import org.apache.kafka.common.protocol.types.Field;
import org.example.nowcoder.entity.Message;
import org.example.nowcoder.entity.Page;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.MessageService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

import static org.example.nowcoder.utils.ForumConstant.*;

/**
 * @author zhaoshuai
 */
@Controller
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;
    private final HostHolder hostHolder;
    private final UserService userService;


    @GetMapping("/letter/list")
    public String getLetterList(Model model, Page page) {
        User user = hostHolder.getUser();
        page.setPageSize(5);
        // PageHelper对象
        PageInfo<Message> conversationPageInfo = messageService.findConversations(user.getId(), page.getPageNum(), page.getPageSize());

        page.setTotal(conversationPageInfo.getTotal());
        page.setPages(conversationPageInfo.getPages());
        page.setNavigatepageNums(conversationPageInfo.getNavigatepageNums());
        page.setPath("/letter/list");

        // 所有会话列表
        List<Message> conversationList = conversationPageInfo.getList();
        List<Map<String, Object>> conversations = new ArrayList<>();
        if (conversationList != null) {
            // 其中一个会话
            for (Message message : conversationList) {
                Map<String, Object> map = new HashMap<>();
                map.put("conversation", message);
                map.put("letterCount", conversationPageInfo.getTotal());
                // 本次会话中的未读消息数
                map.put("unreadCount", messageService.findUnreadCount(user.getId(), message.getConversationId()));
                // 对面的用户
                int targetId = user.getId() == message.getFromId() ? message.getToId() : message.getFromId();
                map.put("target", userService.findUserById(targetId));

                conversations.add(map);
            }
        }
        model.addAttribute("conversations", conversations);

        // 所有未读消息数
        int letterUnreadCount = messageService.findUnreadCount(user.getId(), null);
        model.addAttribute("letterUnreadCount", letterUnreadCount);
        return "site/letter";

    }

    @GetMapping("/letter/detail/{conversationId}")
    public String getLetterDetail(@PathVariable("conversationId") String conversationId, Page page, Model model) {
        // 分页信息
        page.setPageSize(5);
        PageInfo<Message> letterPageInfo = messageService.findDms(conversationId, page.getPageNum(), page.getPageSize());

        page.setTotal(letterPageInfo.getTotal());
        page.setPages(letterPageInfo.getPages());
        page.setNavigatepageNums(letterPageInfo.getNavigatepageNums());
        page.setPath("/letter/detail/" + conversationId);

        // 私信列表
        List<Message> letterList = letterPageInfo.getList();
        List<Map<String, Object>> letters = new ArrayList<>();
        if (letterList != null) {
            for (Message letter : letterList) {
                Map<String, Object> map = new HashMap<>();
                map.put("letter", letter);
                map.put("fromUser", userService.findUserById(letter.getFromId()));

                letters.add(map);
            }
        }
        model.addAttribute("letters", letters);
        // 私信目标
        model.addAttribute("target", getLetterTarget(conversationId));
        return "/site/letter-detail";
    }

    private User getLetterTarget(String conversationId) {
        String[] ids = conversationId.split("_");
        int id0 = Integer.parseInt(ids[0]);
        int id1 = Integer.parseInt(ids[1]);

        // 返回我对面的那个人
        if (hostHolder.getUser().getId() == id0) {
            return userService.findUserById(id1);
        } else {
            return userService.findUserById(id0);
        }
    }

    @PostMapping("/letter/send")
    @ResponseBody
    public String sendLetter(String toName, String content) {
        User targetUser = userService.findUserByName(toName);
        if (targetUser == null) {
            return ForumUtil.getJsonString(1, "target user does not exist");
        }

        Message message = new Message();
        message.setFromId(hostHolder.getUser().getId());
        message.setToId(targetUser.getId());
        if (message.getFromId() < message.getToId()) {
            message.setConversationId(message.getFromId() + "_" + message.getToId());
        } else {
            message.setConversationId(message.getToId() + "_" + message.getFromId());
        }
        message.setContent(content);
        message.setCreateTime(new Date());
        messageService.addMessage(message);
        return ForumUtil.getJsonString(0);

    }

    private List<Integer> getLetterIds(List<Message> letterList) {
        List<Integer> ids = new ArrayList<>();

        if (letterList != null) {
            for (Message message : letterList) {
                if (hostHolder.getUser().getId() == message.getToId() && message.getStatus() == 0) {
                    ids.add(message.getId());
                }
            }
        }

        return ids;
    }

    @GetMapping("/notice/list")
    public String getNoticeList(Model model) {
        User user = hostHolder.getUser();
        System.out.println("-------------------------------------");
        // 查询评论类通知
        Message message = messageService.findLatestNotice(user.getId(), TOPIC_COMMENT);
        Map<String, Object> commentNotice = buildNoticeData(message, TOPIC_COMMENT, user.getId());
        model.addAttribute("commentNotice", commentNotice);

        // 查询点赞类通知
        message = messageService.findLatestNotice(user.getId(), TOPIC_LIKE);
        Map<String, Object> likeNotice = buildNoticeData(message, TOPIC_LIKE, user.getId());
        model.addAttribute("likeNotice", likeNotice);

        // 查询关注类通知
        message = messageService.findLatestNotice(user.getId(), TOPIC_FOLLOW);
        Map<String, Object> followNotice = buildNoticeData(message, TOPIC_FOLLOW, user.getId());
        model.addAttribute("followNotice", followNotice);

        // 查询未读私信数量
        int unreadCount = messageService.findUnreadCount(user.getId(), null);
        model.addAttribute("letterUnreadCount", unreadCount);
        int noticeUnreadCount = messageService.findNoticeUnreadCount(user.getId(), null);
        model.addAttribute("noticeUnreadCount", noticeUnreadCount);
        return "/site/notice";
    }

    @GetMapping("/notice/detail/{topic}")
    public String getNoticeDetail(@PathVariable("topic") String topic, Page page, Model model) {
        User user = hostHolder.getUser();

        page.setPageSize(5);
        page.setPath("/notice/detail/" + topic);
        page.setTotal(messageService.findNoticeCount(user.getId(), topic));

        PageInfo<Message> noticePageInfo = messageService.findNotices(user.getId(), topic, page.getPageNum(), page.getPageSize());
        List<Message> noticeList = noticePageInfo.getList();
        List<Map<String, Object>> notices = new ArrayList<>();
        if (noticeList != null) {
            for (Message notice : noticeList) {
                HashMap<String, Object> map = new HashMap<>();
                // 通知
                map.put("notice", notice);
                //  内容
                String content = HtmlUtils.htmlUnescape(notice.getContent());
                Map<String, Object> data = JSONObject.parseObject(content, HashMap.class);
                map.put("user", userService.findUserById((Integer) data.get("userId")));
                map.put("entityType", data.get("entityType"));
                map.put("entityId", data.get("entityId"));
                map.put("postId", data.get("postId"));

                // 通知作者
                map.put("fromUser", userService.findUserById(notice.getFromId()));
                notices.add(map);
            }

        }
        model.addAttribute("notices", notices);

        // 设置已读
        List<Integer> ids = getLetterIds(noticeList);
        if (!ids.isEmpty()) {
            messageService.updateStatus(ids, 1);
        }
        return "/site/notice-detail";
    }

    private Map<String, Object> buildNoticeData(Message message, String topic, int userId) {
        Map<String, Object> notice = new HashMap<>();
        if (message == null) {
            notice.put("message", null);
            return notice;
        }
        notice.put("message", message);
        String content = HtmlUtils.htmlUnescape(message.getContent());
        Map<String, Object> data = JSONObject.parseObject(content, new TypeReference<>() {
        });
        notice.put("user", userService.findUserById((Integer) data.get("userId")));
        notice.put("entityType", data.get("entityType"));
        notice.put("entityId", data.get("entityId"));
        if (!Objects.equals(topic, TOPIC_FOLLOW)) {
            notice.put("postId", data.get("postId"));
        }

        notice.put("count", messageService.findNoticeCount(userId, topic));
        notice.put("unread", messageService.findNoticeUnreadCount(userId, topic));

        return notice;

    }
}

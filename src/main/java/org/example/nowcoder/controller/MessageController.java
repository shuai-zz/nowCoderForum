package org.example.nowcoder.controller;

import com.github.pagehelper.PageInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.Host;
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

import java.util.*;

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
    public String getLetterList(Model model, Page page){
        User user = hostHolder.getUser();
        page.setPageSize(5);
        // PageHelper对象
        PageInfo<Message> conversationPageInfo= messageService.findConversations(user.getId(),page.getPageNum(),page.getPageSize());

        page.setTotal(conversationPageInfo.getTotal());
        page.setPages(conversationPageInfo.getPages());
        page.setNavigatepageNums(conversationPageInfo.getNavigatepageNums());
        page.setPath("/letter/list");

        // 所有会话列表
        List<Message> conversationList=conversationPageInfo.getList();
        List<Map<String,Object>> conversations=new ArrayList<>();
        if(conversationList!=null){
            // 其中一个会话
            for(Message message:conversationList){
                Map<String,Object> map= new HashMap<>();
                map.put("conversation",message);
                map.put("letterCount",conversationPageInfo.getTotal());
                // 本次会话中的未读消息数
                map.put("unreadCount",messageService.findUnreadCount(user.getId(),message.getConversationId()));
                // 对面的用户
                int targetId=user.getId()==message.getFromId()?message.getToId():message.getFromId();
                map.put("target",userService.findUserById(targetId));

                conversations.add(map);
            }
        }
        model.addAttribute("conversations",conversations);

        // 所有未读消息数
        int letterUnreadCount=messageService.findUnreadCount(user.getId(),null);
        model.addAttribute("letterUnreadCount",letterUnreadCount);
        return "site/letter";

    }

    @GetMapping("/letter/detail/{conversationId}")
    public String getLetterDetail(@PathVariable("conversationId") String conversationId, Page page, Model model){
        // 分页信息
        page.setPageSize(5);
        PageInfo<Message> letterPageInfo= messageService.findDms(conversationId,page.getPageNum(),page.getPageSize());

        page.setTotal(letterPageInfo.getTotal());
        page.setPages(letterPageInfo.getPages());
        page.setNavigatepageNums(letterPageInfo.getNavigatepageNums());
        page.setPath("/letter/detail/"+conversationId);

        // 私信列表
        List<Message> letterList=letterPageInfo.getList();
        List<Map<String,Object>> letters=new ArrayList<>();
        if(letterList!=null){
            for(Message letter:letterList){
                Map<String,Object> map= new HashMap<>();
                map.put("letter",letter);
                map.put("fromUser",userService.findUserById(letter.getFromId()));

                letters.add(map);
            }
        }
        model.addAttribute("letters",letters);
        // 私信目标
        model.addAttribute("target",getLetterTarget(conversationId));
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
    public String sendLetter(String toName, String content){
        User targetUser=userService.findUserByName(toName);
        if(targetUser==null){
            return ForumUtil.getJsonString(1,"target user does not exist");
        }

        Message message = new Message();
        message.setFromId(hostHolder.getUser().getId());
        message.setToId(targetUser.getId());
        if (message.getFromId()<message.getToId()){
            message.setConversationId(message.getFromId()+"_"+message.getToId());
        }else{
            message.setConversationId(message.getToId()+"_"+message.getFromId());
        }
        message.setContent(content);
        message.setCreateTime(new Date());
        messageService.addMessage(message);
        return ForumUtil.getJsonString(0);

    }
}

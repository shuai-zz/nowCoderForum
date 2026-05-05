package com.example.message.interfaces.rest;

import com.example.message.application.dto.MessageItem;
import com.example.message.application.service.MessageService;
import com.example.message.domain.entity.Message;
import com.example.message.interfaces.dto.SendLetterRequest;
import com.example.message.interfaces.vo.ConversationDetailVO;
import com.example.message.interfaces.vo.ConversationVO;
import com.example.message.interfaces.vo.DmVo;
import com.example.shared.exception.ResourceNotFoundException;
import com.example.shared.result.PageData;
import com.example.shared.result.PageResult;
import com.example.shared.result.Result;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * @author zhaoshuai
 */
@Tag(name = "Message", description = "私信会话与消息")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;

    @Operation(summary = "当前用户的会话列表（每个会话的最新一条）")
    @GetMapping("/conversations")
    public Result<PageResult<ConversationVO>> conversations(
            @AuthenticationPrincipal User me,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        PageData<MessageItem> pageData = messageService.findConversations(me.getId(), pageNum, pageSize);
        List<ConversationVO> list = pageData.items().stream()
                .map(conv -> {
                    String conversationId = conv.conversationId();
                    UserVO target = UserVO.from(conv.to());
                    String lastContent = conv.content();
                    Date lastCreateTime = conv.createTime();
                    int dmCount = conv.dmCount();
                    int unreadDmCount = conv.unreadDmCount();
                    return ConversationVO.of(conversationId, target, lastContent, lastCreateTime, dmCount, unreadDmCount);
                }).toList();
        return Result.ok(PageResult.of(list, pageData.total(), pageNum, pageSize));
    }

    @Operation(summary = "某个会话的消息详情 + 对方用户信息")
    @GetMapping("/conversations/{conversationId}")
    public Result<ConversationDetailVO> conversationDetail(
            @AuthenticationPrincipal User me,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        User target = resolveTargetInConversation(me, conversationId);

        PageData<MessageItem> page = messageService.findDms(conversationId, pageNum, pageSize);
        List<DmVo> list = page.items().stream()
                .map(item -> {
                    int id = item.id();
                    UserVO from = UserVO.from(item.from());
                    String content = item.content();
                    int status = item.status();
                    Date createTime = item.createTime();
                    return DmVo.of(id, from, content, status, createTime);
                }).toList();
        return Result.ok(new ConversationDetailVO(UserVO.from(target),PageResult.of(list, page.total(), pageNum, pageSize)));
    }

    @Operation(summary = "发送私信")
    @PostMapping
    public Result<Void> send(@AuthenticationPrincipal User me, @Valid @RequestBody SendLetterRequest req) {
        User target = userService.findUserByName(req.toName());
        if (target == null) {
            throw new ResourceNotFoundException("Target user not found: " + req.toName());
        }

        Message m = new Message();
        m.setFromId(me.getId());
        m.setToId(target.getId());
        m.setConversationId(buildConversationId(me.getId(), target.getId()));
        m.setContent(req.content());
        m.setCreateTime(new Date());
        messageService.addMessage(m);
        return Result.ok();
    }

    // ---- helpers ----

    private User resolveTargetInConversation(User me, String conversationId) {
        String[] ids = conversationId.split("_");
        if (ids.length != 2) {
            throw new ResourceNotFoundException("Invalid conversation id: " + conversationId);
        }
        int id0 = Integer.parseInt(ids[0]);
        int id1 = Integer.parseInt(ids[1]);
        int targetId = me.getId() == id0 ? id1 : id0;
        User target = userService.getById(targetId);
        if (target == null) {
            throw new ResourceNotFoundException("Target user not found in conversation");
        }
        return target;
    }

    private String buildConversationId(int a, int b) {
        return a < b ? (a + "_" + b) : (b + "_" + a);
    }
}

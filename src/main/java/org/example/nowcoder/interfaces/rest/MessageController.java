package org.example.nowcoder.interfaces.rest;

import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.domain.entity.Message;
import org.example.nowcoder.domain.entity.User;
import org.example.nowcoder.exception.ResourceNotFoundException;
import org.example.nowcoder.application.service.MessageService;
import org.example.nowcoder.application.service.UserService;
import org.example.nowcoder.infrastructure.util.HostHolder;
import org.example.nowcoder.interfaces.common.PageResult;
import org.example.nowcoder.interfaces.common.Result;
import org.example.nowcoder.interfaces.dto.SendLetterRequest;
import org.example.nowcoder.interfaces.vo.ConversationDetailVO;
import org.example.nowcoder.interfaces.vo.ConversationVO;
import org.example.nowcoder.interfaces.vo.LetterVO;
import org.example.nowcoder.interfaces.vo.UserVO;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Tag(name = "Message", description = "私信会话与消息")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;
    private final HostHolder hostHolder;

    @Operation(summary = "当前用户的会话列表（每个会话的最新一条）")
    @GetMapping("/conversations")
    public Result<PageResult<ConversationVO>> conversations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        User me = hostHolder.getUser();
        PageInfo<Message> page = messageService.findConversations(me.getId(), pageNum, pageSize);

        List<ConversationVO> list = new ArrayList<>();
        if (page.getList() != null) {
            for (Message c : page.getList()) {
                int targetId = me.getId() == c.getFromId() ? c.getToId() : c.getFromId();
                UserVO target = UserVO.from(userService.getById(targetId));
                int letterCount = messageService.findDmCount(c.getConversationId());
                int unreadCount = messageService.findUnreadCount(me.getId(), c.getConversationId());

                list.add(new ConversationVO(
                        c.getConversationId(),
                        target,
                        c.getContent(),
                        c.getCreateTime(),
                        letterCount,
                        unreadCount
                ));
            }
        }
        return Result.ok(new PageResult<>(
                list, page.getTotal(), page.getPageNum(), page.getPageSize(), page.getPages()));
    }

    @Operation(summary = "某个会话的消息详情 + 对方用户信息")
    @GetMapping("/conversations/{conversationId}")
    public Result<ConversationDetailVO> conversationDetail(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        User me = hostHolder.getUser();
        User target = resolveTargetInConversation(me, conversationId);

        PageInfo<Message> page = messageService.findDms(conversationId, pageNum, pageSize);
        List<LetterVO> items = new ArrayList<>();
        if (page.getList() != null) {
            for (Message m : page.getList()) {
                UserVO from = UserVO.from(userService.getById(m.getFromId()));
                items.add(LetterVO.of(m, from));
            }
        }
        PageResult<LetterVO> letters = new PageResult<>(
                items, page.getTotal(), page.getPageNum(), page.getPageSize(), page.getPages());
        return Result.ok(new ConversationDetailVO(UserVO.from(target), letters));
    }

    @Operation(summary = "发送私信")
    @PostMapping
    public Result<Void> send(@Valid @RequestBody SendLetterRequest req) {
        User me = hostHolder.getUser();
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

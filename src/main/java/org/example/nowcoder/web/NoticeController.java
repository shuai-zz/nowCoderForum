package org.example.nowcoder.web;

import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Message;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.MessageService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.HostHolder;
import org.example.nowcoder.web.common.NoticeContentParser;
import org.example.nowcoder.web.common.PageResult;
import org.example.nowcoder.web.common.Result;
import org.example.nowcoder.web.vo.NoticeOverviewVO;
import org.example.nowcoder.web.vo.NoticeSummaryVO;
import org.example.nowcoder.web.vo.NoticeVO;
import org.example.nowcoder.web.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.example.nowcoder.utils.ForumConstant.*;

@Tag(name = "Notice", description = "通知中心（评论 / 点赞 / 关注）")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final MessageService messageService;
    private final UserService userService;
    private final HostHolder hostHolder;
    private final NoticeContentParser noticeContentParser;

    @Operation(summary = "通知中心概览：三个 topic 的最新一条 + 各自计数 + 总未读")
    @GetMapping
    public Result<NoticeOverviewVO> overview() {
        User me = hostHolder.getUser();
        return Result.ok(new NoticeOverviewVO(
                buildSummary(me.getId(), TOPIC_COMMENT),
                buildSummary(me.getId(), TOPIC_LIKE),
                buildSummary(me.getId(), TOPIC_FOLLOW),
                messageService.findUnreadCount(me.getId(), null),
                messageService.findNoticeUnreadCount(me.getId(), null)
        ));
    }

    @Operation(summary = "某个 topic 下的通知分页（副作用：把本页未读通知置为已读）")
    @GetMapping("/{topic}")
    public Result<PageResult<NoticeVO>> listByTopic(
            @PathVariable String topic,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        User me = hostHolder.getUser();
        PageInfo<Message> page = messageService.findNotices(me.getId(), topic, pageNum, pageSize);

        List<NoticeVO> items = new ArrayList<>();
        List<Integer> unreadIds = new ArrayList<>();
        if (page.getList() != null) {
            for (Message msg : page.getList()) {
                Map<String, Object> data = noticeContentParser.parse(msg.getContent());
                UserVO from = UserVO.from(userService.findUserById((Integer) data.get("userId")));
                items.add(new NoticeVO(
                        msg.getId(),
                        topic,
                        from,
                        (Integer) data.get("entityType"),
                        (Integer) data.get("entityId"),
                        (Integer) data.get("postId"),
                        msg.getStatus(),
                        msg.getCreateTime()
                ));
                if (msg.getToId() == me.getId() && msg.getStatus() == 0) {
                    unreadIds.add(msg.getId());
                }
            }
        }
        // 副作用：标记为已读（保持老代码行为）
        if (!unreadIds.isEmpty()) {
            messageService.updateStatus(unreadIds, 1);
        }

        return Result.ok(new PageResult<>(
                items, page.getTotal(), page.getPageNum(), page.getPageSize(), page.getPages()));
    }

    // ---- helpers ----

    private NoticeSummaryVO buildSummary(int userId, String topic) {
        Message latest = messageService.findLatestNotice(userId, topic);
        if (latest == null) {
            return NoticeSummaryVO.empty(topic);
        }
        Map<String, Object> data = noticeContentParser.parse(latest.getContent());
        UserVO lastFrom = UserVO.from(userService.findUserById((Integer) data.get("userId")));
        return new NoticeSummaryVO(
                topic,
                true,
                lastFrom,
                (Integer) data.get("entityType"),
                (Integer) data.get("entityId"),
                (Integer) data.get("postId"),
                messageService.findNoticeCount(userId, topic),
                messageService.findNoticeUnreadCount(userId, topic),
                latest.getCreateTime()
        );
    }
}

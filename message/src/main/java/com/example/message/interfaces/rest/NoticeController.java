package com.example.message.interfaces.rest;

import com.example.message.application.dto.MessageItem;
import com.example.message.application.service.MessageService;
import com.example.message.domain.entity.Message;
import com.example.message.interfaces.common.NoticeContentParser;
import com.example.message.interfaces.vo.NoticeOverviewVO;
import com.example.message.interfaces.vo.NoticeSummaryVO;
import com.example.message.interfaces.vo.NoticeVO;
import com.example.shared.result.PageData;
import com.example.shared.result.PageResult;
import com.example.shared.result.Result;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.shared.constant.ForumConstant.*;


/**
 * @author zhaoshuai
 */
@Tag(name = "Notice", description = "通知中心（评论 / 点赞 / 关注）")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final MessageService messageService;
    private final UserService userService;
    private final NoticeContentParser noticeContentParser;

    @Operation(summary = "通知中心概览：三个 topic 的最新一条 + 各自计数 + 总未读")
    @GetMapping
    public Result<NoticeOverviewVO> overview(@AuthenticationPrincipal User me) {
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
            @AuthenticationPrincipal User me,
            @PathVariable String topic,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        PageData<MessageItem> pageData = messageService.findNotices(me.getId(), topic, pageNum, pageSize);
        if (pageData.items().isEmpty()) {
            return Result.ok(PageResult.empty(pageNum, pageSize));
        }

        List<Map<String, Object>> parsed = pageData.items().stream()
                .map(item -> noticeContentParser.parse(item.content()))
                .toList();

        List<Integer> triggerUserIds = parsed.stream()
                .map(d -> (Integer) d.get("userId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, User> triggerUserMap = userService.listByIds(triggerUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<NoticeVO> items = new ArrayList<>(pageData.items().size());
        List<Integer> unreadIds = new ArrayList<>();
        for (int i = 0; i < pageData.items().size(); i++) {
            MessageItem notice = pageData.items().get(i);
            Map<String, Object> data = parsed.get(i);
            UserVO from = UserVO.from(triggerUserMap.get((Integer) data.get("userId")));
            items.add(NoticeVO.of(
                    notice.id(),
                    topic,
                    from,
                    (Integer) data.get("entityType"),
                    (Integer) data.get("entityId"),
                    (Integer) data.get("postId"),
                    notice.status(),
                    notice.createTime()
            ));
            if (notice.status() == 0) {
                unreadIds.add(notice.id());
            }
        }

        if (!unreadIds.isEmpty()) {
            messageService.updateStatus(unreadIds, 1);
        }

        return Result.ok(PageResult.of(items, pageData.total(), pageNum, pageSize));
    }

    // ---- helpers ----

    private NoticeSummaryVO buildSummary(int userId, String topic) {
        Message latest = messageService.findLatestNotice(userId, topic);
        if (latest == null) {
            return NoticeSummaryVO.empty(topic);
        }
        Map<String, Object> data = noticeContentParser.parse(latest.getContent());
        UserVO lastFrom = UserVO.from(userService.getById((Integer) data.get("userId")));
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

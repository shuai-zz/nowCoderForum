package org.example.nowcoder.controller;

import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Event;
import org.example.nowcoder.entity.Page;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.event.EventProducer;
import org.example.nowcoder.service.FollowService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.example.nowcoder.utils.ForumConstant.ENTITY_TYPE_USER;
import static org.example.nowcoder.utils.ForumConstant.TOPIC_FOLLOW;

/**
 * @author zhaoshuai
 */
@Controller
@RequiredArgsConstructor
public class FollowController {
    private final FollowService followService;
    private final HostHolder hostHolder;
    private final UserService userService;
    private final EventProducer eventProducer;

    @PostMapping("/follow")
    @ResponseBody
    public String follow(int entityType, int entityId) {
        User user = hostHolder.getUser();

        followService.follow(user.getId(), entityType, entityId);

        // 触发关注事件
        Event event = new Event()
                .setTopic(TOPIC_FOLLOW)
                .setUserId(user.getId())
                .setEntityType(entityType)
                .setEntityId(entityId)
                .setEntityUserId(entityId);
        eventProducer.fireEvent(event);
        return ForumUtil.getJsonString(0, "Followed");
    }


    @PostMapping("/unfollow")
    @ResponseBody
    public String unfollow(int entityType, int entityId) {
        User user = hostHolder.getUser();
        followService.unfollow(user.getId(), entityType, entityId);
        return ForumUtil.getJsonString(0, "Unfollowed");
    }

    @GetMapping("/followees/{userId}")
    public String getFollowees(@PathVariable("userId") int userId, Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("User does not exist");
        }
        model.addAttribute("user", user);

        page.setPageSize(5);
        page.setTotal((int) followService.findFolloweeCount(userId, ENTITY_TYPE_USER));
        page.setPath("/followees/" + userId);
        page.setPages((int) (page.getTotal() % page.getPageSize() == 0 ? page.getTotal() / page.getPageSize() : page.getTotal() / page.getPageSize() + 1));
        page.setNavigatepageNums(getNavigatePageNums(page.getPages(), page.getPageNum()));

        List<Map<String, Object>> userList = followService.findFollowees(userId, page.getPageNum(), page.getPageSize());
        if (userList != null) {
            for (Map<String, Object> map : userList) {
                User u = (User) map.get("user");
                map.put("hasFollowed", hasFollowed(u.getId()));
            }
        }
        model.addAttribute("users", userList);
        return "/site/followee";
    }

    @GetMapping("/followers/{userId}")
    public String getFollowers(@PathVariable("userId") int userId, Page page, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("User does not exist");
        }
        model.addAttribute("user", user);

        page.setPageSize(5);
        page.setTotal((int) followService.findFollowerCount(ENTITY_TYPE_USER, userId));
        page.setPath("/followers/" + userId);
        page.setPages((int) (page.getTotal() % page.getPageSize() == 0 ? page.getTotal() / page.getPageSize() : page.getTotal() / page.getPageSize() + 1));
        page.setNavigatepageNums(getNavigatePageNums(page.getPages(), page.getPageNum()));

        List<Map<String, Object>> userList = followService.findFollowers(userId, page.getPageNum(), page.getPageSize());
        if (userList != null) {
            for (Map<String, Object> map : userList) {
                User u = (User) map.get("user");
                map.put("hasFollowed", hasFollowed(u.getId()));
            }
        }
        model.addAttribute("users", userList);
        return "/site/follower";
    }

    private boolean hasFollowed(int targetId) {
        if (hostHolder.getUser() == null) {
            return false;
        }
        return followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, targetId);
    }

    private int[] getNavigatePageNums(int pages, int pageNum) {
        int showCount = 5;

        int startPage = Math.max(1, pageNum - 2);
        int endPage = Math.min(pages, pageNum + 2);

        return IntStream.rangeClosed(startPage, endPage)
                .limit(showCount)
                .toArray();
    }
}

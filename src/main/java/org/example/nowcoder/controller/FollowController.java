package org.example.nowcoder.controller;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.FollowService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author zhaoshuai
 */
@Controller
@RequiredArgsConstructor
public class FollowController {
    private final FollowService followService;
    private final HostHolder hostHolder;

    @PostMapping("/follow")
    @ResponseBody
    public String follow(int entityType, int entityId){
        User user = hostHolder.getUser();

        followService.follow(user.getId(), entityType, entityId);
        return ForumUtil.getJsonString(0, "Followed");
    }


    @PostMapping("/unfollow")
    @ResponseBody
    public String unfollow(int entityType, int entityId){
        User user = hostHolder.getUser();
        followService.unfollow(user.getId(), entityType, entityId);
        return ForumUtil.getJsonString(0, "Unfollowed");
    }
}

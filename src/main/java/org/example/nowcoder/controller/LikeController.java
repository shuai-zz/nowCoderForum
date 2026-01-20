package org.example.nowcoder.controller;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Pointcut;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;

/**
 * @author zhaoshuai
 */
@Controller
@RequiredArgsConstructor
public class LikeController {
    private final LikeService likeService;
    private final HostHolder hostHolder;

    @PostMapping("/like")
    @ResponseBody
    public String like(int entityType, int entityId, int entityUserId){
        User user = hostHolder.getUser();
        // 点赞
        likeService.like(user.getId(), entityType, entityId, entityUserId);
        // 数量
        long likeCount = likeService.findEntityLikeCount(entityType, entityId);
        // 状态
        int likeStatus = likeService.findEntityLikeStatus(user.getId(), entityType, entityId);

        // 返回结果
        HashMap<String, Object> map = new HashMap<>();
        map.put("likeCount", likeCount);
        map.put("likeStatus", likeStatus);
        return ForumUtil.getJsonString(0, null, map);
    }
}

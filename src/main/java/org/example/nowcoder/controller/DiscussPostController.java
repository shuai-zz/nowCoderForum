package org.example.nowcoder.controller;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.Page;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.DiscussPostService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * @author zhaoshuai
 */
@Controller
@RequestMapping("/discuss")
@RequiredArgsConstructor
public class DiscussPostController {
    private final DiscussPostService discussPostService;
    private final HostHolder hostHolder;
    private final UserService userService;

    @PostMapping("/add")
    @ResponseBody
    public String addDiscussPost(String title, String content){
        User user = hostHolder.getUser();
        if(user==null){
            return ForumUtil.getJsonString(403,"you haven't logged in yet!");
        }
        DiscussPost discussPost = new DiscussPost();
        discussPost.setUserId(user.getId());
        discussPost.setTitle(title);
        discussPost.setContent(content);
        discussPost.setCreateTime(new Date());
        discussPostService.insertDiscussPost(discussPost);

        // 报错的情况将来统一处理
        return ForumUtil.getJsonString(0,"Post successfully!");

    }

    @GetMapping("/detail/{discussPostId}")
    public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page){
        // 查询帖子
        DiscussPost post=discussPostService.findDiscussPostById(discussPostId);
        model.addAttribute("post",post);

        User user = userService.findUserById(post.getUserId());
        model.addAttribute("user",user);

        return "/site/discuss-detail";


    }
}

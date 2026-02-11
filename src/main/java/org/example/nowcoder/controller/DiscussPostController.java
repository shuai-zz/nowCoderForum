package org.example.nowcoder.controller;

import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.*;
import org.example.nowcoder.event.EventProducer;
import org.example.nowcoder.service.CommentService;
import org.example.nowcoder.service.DiscussPostService;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.example.nowcoder.utils.ForumConstant.*;

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
    private final CommentService commentService;
    private final LikeService likeService;
    private final EventProducer eventProducer;

    @PostMapping("/add")
    @ResponseBody
    public String addDiscussPost(String title, String content) {
        User user = hostHolder.getUser();
        if (user == null) {
            return ForumUtil.getJsonString(403, "you haven't logged in yet!");
        }
        DiscussPost discussPost = new DiscussPost();
        discussPost.setUserId(user.getId());
        discussPost.setTitle(title);
        discussPost.setContent(content);
        discussPost.setCreateTime(new Date());
        discussPostService.insertDiscussPost(discussPost);
        // 触发发帖事件
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(user.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(discussPost.getId());
        eventProducer.fireEvent(event);

        // 报错的情况将来统一处理
        return ForumUtil.getJsonString(0, "Post successfully!");

    }

    @GetMapping("/detail/{discussPostId}")
    public String getDiscussPost(@PathVariable("discussPostId") int discussPostId, Model model, Page page) {
        // 查询帖子
        DiscussPost post = discussPostService.findDiscussPostById(discussPostId);
        model.addAttribute("post", post);
        // 查询作者
        User user = userService.findUserById(post.getUserId());
        model.addAttribute("user", user);

        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, discussPostId);
        model.addAttribute("likeCount", likeCount);

        // 点赞状态
        int likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_POST, discussPostId);
        model.addAttribute("likeStatus", likeStatus);


        // 评论分页信息
        page.setPageSize(5);
        PageInfo<Comment> commentsPageInfo = commentService.findCommentsByEntity(ENTITY_TYPE_POST, discussPostId, page.getPageNum(), page.getPageSize());
        page.setPath("/discuss/detail/" + discussPostId);
        // 评论总数
        page.setTotal(commentsPageInfo.getTotal());
        // 导航页码
        page.setNavigatepageNums(commentsPageInfo.getNavigatepageNums());
        // 总页数
        page.setPages(commentsPageInfo.getPages());

        List<Map<String, Object>> comments = new ArrayList<>();
        if (commentsPageInfo.getList() != null) {
            for (Comment comment : commentsPageInfo.getList()) {
                Map<String, Object> commmentMap = new HashMap<>();
                // 评论
                commmentMap.put("comment", comment);
                // 评论作者
                commmentMap.put("user", userService.findUserById(comment.getUserId()));
                // 评论点赞数量
                commmentMap.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId()));
                // 评论点赞状态
                commmentMap.put("likeStatus", hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, comment.getId()));


                // 对于评论的回复
                PageInfo<Comment> repliesPageInfo = commentService.findCommentsByEntity(ENTITY_TYPE_COMMENT, comment.getId(), 0, Integer.MAX_VALUE);
                List<Map<String, Object>> replies = new ArrayList<>();
                if (repliesPageInfo != null) {
                    for (Comment reply : repliesPageInfo.getList()) {
                        Map<String, Object> replyMap = new HashMap<>();
                        // 回复
                        replyMap.put("reply", reply);
                        // 回复作者
                        replyMap.put("user", userService.findUserById(reply.getUserId()));
                        // 回复目标
                        User target = reply.getTargetId() == 0 ? null : userService.findUserById(reply.getTargetId());
                        replyMap.put("target", target);
                        // 回复点赞数量
                        replyMap.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, reply.getId()));
                        // 评论点赞状态
                        replyMap.put("likeStatus", hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, reply.getId()));
                        replies.add(replyMap);
                    }
                }
                commmentMap.put("replies", replies);
                long replyCount = repliesPageInfo == null ? 0 : repliesPageInfo.getTotal();
                commmentMap.put("replyCount", replyCount);
                comments.add(commmentMap);
            }
        }
        model.addAttribute("comments", comments);
        return "/site/discuss-detail";
    }

    // 置顶
    @PostMapping("/top")
    @ResponseBody
    public String setTop(int id) {
        discussPostService.updateType(id, 1);

        // 触发发帖事件
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);
        return ForumUtil.getJsonString(0);
    }

    // 加精
    @PostMapping("/wonderful")
    @ResponseBody
    public String setWonderful(int id) {
        discussPostService.updateStatus(id, 1);
        // 触发发帖事件
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);
        return ForumUtil.getJsonString(0);
    }

    // 删除
    @PostMapping("/delete")
    @ResponseBody
    public String setDelete(int id) {
        discussPostService.updateStatus(id, 2);
        // 触发删帖事件
        Event event = new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);
        return ForumUtil.getJsonString(0);
    }
}

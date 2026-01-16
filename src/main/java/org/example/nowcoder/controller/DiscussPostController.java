package org.example.nowcoder.controller;

import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Comment;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.Page;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.CommentService;
import org.example.nowcoder.service.DiscussPostService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.example.nowcoder.utils.ForumConstant.ENTITY_TYPE_COMMENT;
import static org.example.nowcoder.utils.ForumConstant.ENTITY_TYPE_POST;

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
        // 查询作者
        User user = userService.findUserById(post.getUserId());
        model.addAttribute("user",user);


        // 评论分页信息
        page.setPageSize(5);
        PageInfo<Comment> comments = commentService.findCommentsByEntity(ENTITY_TYPE_POST, discussPostId, page.getPageNum(), page.getPageSize());
        page.setPath("/discuss/detail/"+discussPostId);
        // 评论总数
        page.setTotal(comments.getTotal());
        // 导航页码
        page.setNavigatepageNums(comments.getNavigatepageNums());
        // 总页数
        page.setPages(comments.getPages());

        List<Map<String,Object>> commentList=new ArrayList<>();
        for (Comment comment : comments.getList()) {
            Map<String, Object> commmentMap = new HashMap<>();
            commmentMap.put("comment", comment);
            commmentMap.put("user", userService.findUserById(comment.getUserId()));

            // 对于评论的回复
            PageInfo<Comment> replies = commentService.findCommentsByEntity(ENTITY_TYPE_COMMENT, comment.getId(), 0, Integer.MAX_VALUE);
            List<Map<String, Object>> replyList = new ArrayList<>();
            if (replies != null) {
                for (Comment reply : replies.getList()) {
                    Map<String, Object> replyMap = new HashMap<>();
                    replyMap.put("reply", reply);
                    replyMap.put("user", userService.findUserById(reply.getUserId()));
                    User target = reply.getTargetId() == 0 ? null : userService.findUserById(reply.getTargetId());
                    replyMap.put("target", target);
                    replyList.add(replyMap);
                }
            }
            commmentMap.put("replies", replyList);
            long replyCount = replies == null ? 0 : replies.getTotal();
            commmentMap.put("replyCount", replyCount);
            commentList.add(commmentMap);
        }
        model.addAttribute("comments",commentList);
        return "/site/discuss-detail";
    }
}

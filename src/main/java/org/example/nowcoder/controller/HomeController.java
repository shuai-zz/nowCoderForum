package org.example.nowcoder.controller;

import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.bcel.Const;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.Page;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.DiscussPostService;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

import static org.example.nowcoder.utils.ForumConstant.ENTITY_TYPE_POST;

/**
 * @author 23211
 */
@Controller
@RequiredArgsConstructor
public class HomeController {
    private final UserService userService;
    private final DiscussPostService discussPostService;
    private final LikeService likeService;
    // 当userId为0时，查询所有用户
    private static final int ALL_USERS=0;

    @GetMapping("/index")
    public String getIndex(Model model, Page page){
        PageInfo<DiscussPost> discussPostPageInfo = discussPostService.selectDiscussPosts(ALL_USERS, page.getPageNum(), page.getPageSize());
        List<Map<String, Object>> discussPosts=new ArrayList<>();

        page.setTotal(discussPostPageInfo.getTotal());
        page.setPages(discussPostPageInfo.getPages());
        page.setNavigatepageNums(discussPostPageInfo.getNavigatepageNums());
        page.setPath("/index");

        List<DiscussPost> list = discussPostPageInfo.getList();
        if(list!=null){
            for(DiscussPost post: list){
                Map<String, Object> map=new HashMap<>();
                map.put("post",post);
                User user=userService.findUserById(post.getUserId());
                map.put("user",user);
                long likeCount=likeService.queryEntityLikeCount(ENTITY_TYPE_POST,post.getId());
                map.put("likeCount",likeCount);
                discussPosts.add(map);
            }
        }

        model.addAttribute("discussPosts",discussPosts);
        return "index";
    }

    @GetMapping("/error")
    public String getErrorPage(){
        return "error/500";
    }
}

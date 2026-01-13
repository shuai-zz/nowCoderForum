package org.example.nowcoder.controller;

import io.netty.util.internal.StringUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Host;
import org.apache.commons.lang3.StringUtils;
import org.example.nowcoder.annotation.LoginRequired;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author zhaoshuai
 */
@Controller
@RequestMapping("/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {
    private final HostHolder hostHolder;
    private final UserService userService;

    @Value("${nowCoder.path.upload}")
    private String uploadPath;
    @Value("${nowCoder.path.domain}")
    private String domain;
    @Value("${server.servlet.context-path}")
    private String contextPath;


    @LoginRequired
    @GetMapping("/setting")
    public String getSettingPage(){
        return "/site/setting";
    }

    @LoginRequired
    @PostMapping("/upload")
    public String uploadHeader(MultipartFile avatarImg, Model model){
       if(avatarImg==null){
           model.addAttribute("error","Please select an image");
           return "/site/setting";
       }
       String filename=avatarImg.getOriginalFilename();
        String extension= null;
        // 防止文件名为空
        extension = filename != null ? filename.substring(filename.lastIndexOf(".")) : null;
        // 检查文件格式是否正确
        if(StringUtils.isBlank(extension)||!(".jpg".equals(extension)|| ".png".equals(extension)|| ".jpeg".equals(extension))){
            model.addAttribute("error","File format is not supported");
            return "/site/setting";
        }

        filename= ForumUtil.generateUuid()+extension;
        File dest=new File(uploadPath+"/"+filename);
        try {
            avatarImg.transferTo(dest);
        } catch (IOException e) {
            log.error("Failed to upload file:{}",e.getMessage());
            throw new RuntimeException("Failed to upload file, server error",e);
        }

        // 更新用户头像路径
        User user = hostHolder.getUser();
        String avatarUrl=domain+contextPath+"/user/avatar/"+filename;
        userService.updateAvatar(user.getId(), avatarUrl);
        return "redirect:/index";
    }

    @GetMapping("/avatar/{filename}")
    public void getAvatar(@PathVariable("filename") String filename, HttpServletResponse response){
        filename=uploadPath+"/"+filename;
        String extension = filename.substring(filename.lastIndexOf("."));
        response.setContentType("image/"+extension);

        try(OutputStream os=response.getOutputStream();
            FileInputStream fis=new FileInputStream(filename)){
            byte[] buffer=new byte[1024];
            int b=0;
            while((b=fis.read(buffer))!=-1){
                os.write(buffer,0,b);
            }
        }catch (IOException e){
            log.error("Failed to get avatar:{}",e.getMessage());
        }

    }

    @PostMapping("/updatePassword")
    public String updatePassword(String oldPassword,String newPassword,String confirmPassword, Model model){
        if(!confirmPassword.equals(newPassword)){
            model.addAttribute("confirmPasswordMsg","Password do not match");
            return "site/setting";
        }
        User user = hostHolder.getUser();
        Map<String , Object> map = userService.updatePassword(user.getId(), oldPassword, newPassword);
        if(map==null||map.isEmpty()){
            model.addAttribute("msg", "Password updated successfully");
            model.addAttribute("target", "/index");
            return "site/operate-result";
        }else{
            model.addAttribute("oldPasswordMsg",map.get("oldPasswordMsg"));
            model.addAttribute("newPasswordMsg",map.get("newPasswordMsg"));
            return "site/setting";
        }
    }

    @GetMapping("/profile/{userId}")
    public String getProfilePage(@PathVariable("userId") int userId, Model model){
        User user = userService.findUserById(userId);
        if(user==null){
            throw new RuntimeException("User does not exist");
        }
        model.addAttribute("user",user);
        return "site/profile";
    }
}

package org.example.nowcoder.controller;

import com.google.code.kaptcha.Producer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.config.KaptchaConfig;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static org.example.nowcoder.utils.ForumConstant.ACTIVATION_REPEAT;
import static org.example.nowcoder.utils.ForumConstant.ACTIVATION_SUCCESS;

/**
 * @author 23211
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class LoginController {
    private final UserService userService;
    private final Producer kaptchaProducer;


    @GetMapping("/register")
    public String getRegisterPage() {
        return "/site/register";
    }

    @GetMapping("/login")
    public String getLoginPage() {
        return "/site/login";
    }

    @PostMapping("/register")
    public String register(Model model, User user, @RequestParam("confirmPassword") String confirmPassword) {
        if (!user.getPassword().equals(confirmPassword)) {
            model.addAttribute("passwordMsg", "Passwords do not match");
            return "/site/register";
        }
        Map<String, Object> map = userService.register(user);
        if (map == null || map.isEmpty()) {
            model.addAttribute("msg", "Register successfully, please check your email for activation");
            model.addAttribute("target", "/index");
            return "/site/operate-result";
        } else {
            model.addAttribute("usernameMsg", map.get("usernameMsg"));
            model.addAttribute("passwordMsg", map.get("passwordMsg"));
            model.addAttribute("emailMsg", map.get("emailMsg"));
            return "/site/register";
        }
    }

    @GetMapping("/activation/{userId}/{code}")
    public String activation(Model model, @PathVariable("userId") int userId, @PathVariable("code") String code){
        int result=userService.activation(userId,code);
        if(result==ACTIVATION_SUCCESS){
            model.addAttribute("msg","Activation successfully");
            model.addAttribute("target","/login");
        }else if(result==ACTIVATION_REPEAT){
            model.addAttribute("msg","This account has been activated");
            model.addAttribute("target","/index");
        }else{
            model.addAttribute("msg","Activation failed, invalid activation code");
            model.addAttribute("target","/index");
        }
        return "/site/operate-result";
    }

    @GetMapping("/kaptcha")
    public void getKaptcha(HttpServletResponse response, HttpSession session){
        // 生成验证码
        String text = kaptchaProducer.createText();
        BufferedImage image = kaptchaProducer.createImage(text);

        // 将验证码存入session
        session.setAttribute("kaptcha", text);

        // 将图片输出给浏览器
        response.setContentType("image/png");
        try {
            OutputStream os=response.getOutputStream();
            ImageIO.write(image, "png", os);
        } catch (IOException e) {
            log.error("Failed to output image:{}", e.getMessage());
        }


    }
}

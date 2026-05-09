package com.example.user.interfaces.rest;

import com.example.shared.exception.ValidationException;
import com.example.shared.result.Result;
import com.example.user.application.service.KaptchaService;
import com.example.user.application.service.UserService;
import com.example.user.domain.entity.User;
import com.example.user.interfaces.dto.LoginRequest;
import com.example.user.interfaces.dto.RegisterRequest;
import com.example.user.interfaces.vo.UserVO;
import com.example.shared.captcha.CaptchaContext;
import com.example.shared.captcha.CaptchaVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import com.example.user.interfaces.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.shared.constant.ForumConstant.*;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;


/**
 * @author zhaoshuai
 */
@Tag(name = "Auth", description = "认证相关：注册 / 激活 / 登录 / 登出 / 验证码 / 当前用户")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final KaptchaService kaptchaService;
    private final CaptchaVerifier captchaVerifier;

    @Operation(summary = "注册：成功后向注册邮箱发送激活邮件")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new ValidationException("Passwords do not match");
        }
        User user = User.builder()
                .username(req.username())
                .password(req.password())
                .email(req.email())
                .build();
        userService.register(user);
        return Result.ok();
    }

    @Operation(summary = "激活账户：由前端激活页调用（邮件链接点进去的落地页）")
    @PostMapping("/activate/{userId}/{code}")
    public Result<Void> activate(@PathVariable int userId, @PathVariable String code) {
        userService.activation(userId, code);
        return Result.ok();
    }

    @Operation(summary = "图形验证码（仅 Kaptcha 模式使用）：响应头 X-Captcha-Owner 返回 owner")
    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public void captcha(HttpServletResponse response) throws IOException {
        KaptchaService.Captcha captcha = kaptchaService.issue();

        response.setHeader("X-Captcha-Owner", captcha.owner());
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try (OutputStream os = response.getOutputStream()) {
            ImageIO.write(captcha.image(), "png", os);
        }
    }

    @Operation(summary = "登录：验证码 + 账号密码校验，成功返回 ticket")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        // 验证码校验
        captchaVerifier.verify(new CaptchaContext(
                req.captcha(), req.captchaOwner(),
                req.captchaTicket(), req.captchaRandstr(),
                clientIp(httpReq)
        ));

        // 登录
        int expiredSeconds = req.rememberMe() ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;
        var result = userService.login(req.username(), req.password(), expiredSeconds);

        return Result.ok(new LoginVO(result.ticket(), expiredSeconds, UserVO.from(result.user())));
    }

    @Operation(summary = "登出：作废当前 Bearer ticket")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String ticket = authHeader.substring("Bearer ".length()).trim();
            userService.logout(ticket);
        }
        return Result.ok();
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me(@AuthenticationPrincipal User user) {
        return Result.ok(UserVO.from(user));
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = req.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real : req.getRemoteAddr();
    }
}

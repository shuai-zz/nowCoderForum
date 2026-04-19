package org.example.nowcoder.web;

import com.google.code.kaptcha.Producer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.captcha.CaptchaContext;
import org.example.nowcoder.captcha.CaptchaVerifier;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.exception.AuthException;
import org.example.nowcoder.exception.ValidationException;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.example.nowcoder.utils.RedisKeyUtil;
import org.example.nowcoder.web.common.Result;
import org.example.nowcoder.web.dto.LoginRequest;
import org.example.nowcoder.web.dto.RegisterRequest;
import org.example.nowcoder.web.vo.LoginVO;
import org.example.nowcoder.web.vo.UserVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.example.nowcoder.utils.ForumConstant.*;

@Tag(name = "Auth", description = "认证相关：注册 / 激活 / 登录 / 登出 / 验证码 / 当前用户")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final int CAPTCHA_EXPIRE_SECONDS = 60;

    private final UserService userService;
    private final Producer kaptchaProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final HostHolder hostHolder;
    private final CaptchaVerifier captchaVerifier;

    @Operation(summary = "注册：成功后向注册邮箱发送激活邮件")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new ValidationException("Passwords do not match");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(req.password());
        user.setEmail(req.email());

        Map<String, Object> errors = userService.register(user);
        if (errors != null && !errors.isEmpty()) {
            throw new ValidationException(joinErrors(errors));
        }
        return Result.ok();
    }

    @Operation(summary = "激活账户：由前端激活页调用（邮件链接点进去的落地页）")
    @PostMapping("/activate/{userId}/{code}")
    public Result<Void> activate(@PathVariable int userId, @PathVariable String code) {
        int result = userService.activation(userId, code);
        return switch (result) {
            case ACTIVATION_SUCCESS -> Result.ok();
            case ACTIVATION_REPEAT -> Result.fail(1, "Account already activated");
            default -> throw new ValidationException("Invalid activation code");
        };
    }

    @Operation(summary = "图形验证码（仅 Kaptcha 模式使用）：响应头 X-Captcha-Owner 返回 owner")
    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public void captcha(HttpServletResponse response) throws IOException {
        String text = kaptchaProducer.createText();
        BufferedImage image = kaptchaProducer.createImage(text);

        String owner = ForumUtil.generateUuid();
        String redisKey = RedisKeyUtil.getKaptchaKey(owner);
        redisTemplate.opsForValue().set(redisKey, text, CAPTCHA_EXPIRE_SECONDS, TimeUnit.SECONDS);

        response.setHeader("X-Captcha-Owner", owner);
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try (OutputStream os = response.getOutputStream()) {
            ImageIO.write(image, "png", os);
        }
    }

    @Operation(summary = "登录：验证码 + 账号密码校验，成功返回 ticket")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        captchaVerifier.verify(new CaptchaContext(
                req.captcha(), req.captchaOwner(),
                req.captchaTicket(), req.captchaRandstr(),
                clientIp(httpReq)
        ));

        int expiredSeconds = req.rememberMe() ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;
        Map<String, Object> result = userService.login(req.username(), req.password(), expiredSeconds);

        if (!result.containsKey("ticket")) {
            throw new AuthException(joinErrors(result));
        }

        String ticket = (String) result.get("ticket");
        User user = userService.findUserByName(req.username());
        return Result.ok(new LoginVO(ticket, expiredSeconds, UserVO.from(user)));
    }

    @Operation(summary = "登出：作废当前 Bearer ticket")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String ticket = authHeader.substring("Bearer ".length()).trim();
            userService.logout(ticket);
        }
        SecurityContextHolder.clearContext();
        return Result.ok();
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = hostHolder.getUser();
        if (user == null) {
            throw new AuthException("Not authenticated");
        }
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

    private String joinErrors(Map<String, Object> errors) {
        return errors.values().stream()
                .map(Object::toString)
                .collect(Collectors.joining("; "));
    }
}

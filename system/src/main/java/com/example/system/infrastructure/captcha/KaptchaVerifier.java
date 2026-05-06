package com.example.system.infrastructure.captcha;

import com.example.shared.captcha.CaptchaContext;
import com.example.shared.captcha.CaptchaVerifier;
import com.example.shared.exception.ValidationException;
import com.example.shared.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 默认实现：读取 Redis 中存放的验证码，与用户输入比对。
 * @author zhaoshuai
 */
@Component
@ConditionalOnProperty(prefix = "nowCoder.captcha", name = "provider", havingValue = "kaptcha", matchIfMissing = true)
@RequiredArgsConstructor
public class KaptchaVerifier implements CaptchaVerifier {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void verify(CaptchaContext ctx) {
        if (StringUtils.isBlank(ctx.kaptchaOwner()) || StringUtils.isBlank(ctx.kaptchaText())) {
            throw new ValidationException("Captcha is required");
        }
        String expected = redisTemplate.opsForValue().get(RedisKeyUtil.getKaptchaKey(ctx.kaptchaOwner()));
        if (StringUtils.isBlank(expected) || !expected.equalsIgnoreCase(ctx.kaptchaText())) {
            throw new ValidationException("Captcha is incorrect or expired");
        }
    }
}

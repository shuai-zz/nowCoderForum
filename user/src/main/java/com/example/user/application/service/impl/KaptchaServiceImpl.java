package com.example.user.application.service.impl;

import com.example.shared.utils.ForumUtil;
import com.example.shared.utils.RedisKeyUtil;
import com.example.user.application.service.KaptchaService;
import com.google.code.kaptcha.Producer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class KaptchaServiceImpl implements KaptchaService {

    private static final int EXPIRE_SECONDS = 60;

    private final Producer kaptchaProducer;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public Captcha issue() {
        String text = kaptchaProducer.createText();
        BufferedImage image = kaptchaProducer.createImage(text);
        String owner = ForumUtil.generateUuid();
        redisTemplate.opsForValue()
                .set(RedisKeyUtil.getKaptchaKey(owner), text, EXPIRE_SECONDS, TimeUnit.SECONDS);
        return new Captcha(image, owner);
    }
}

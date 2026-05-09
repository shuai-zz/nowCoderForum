package com.example.user.application.service;

import java.awt.image.BufferedImage;

/**
 * 本地图形验证码（Kaptcha）发放服务。
 * <p>把 captcha 生成 + Redis 缓存从 Controller 抽出来，避免接口层直接接触基础设施。
 * 与 {@code shared.captcha.CaptchaVerifier} 是分工关系：本服务只负责发放，校验交给 verifier。
 */
public interface KaptchaService {

    /**
     * 发放一个新验证码：生成图片 + 关联 owner，把答案缓存到 Redis（默认 60s 过期）。
     *
     * @return image + owner（owner 由前端保留，登录时回传以做校验）
     */
    Captcha issue();

    record Captcha(BufferedImage image, String owner) {}
}

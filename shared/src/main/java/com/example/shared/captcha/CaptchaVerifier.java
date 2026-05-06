package com.example.shared.captcha;

import com.example.shared.exception.ValidationException;

/**
 * 验证码校验器接口。校验失败时应抛出 {@link ValidationException}。
 *
 * <p>实现由配置 {@code nowCoder.captcha.provider} 选择：
 * <ul>
 *   <li>{@code kaptcha}（默认）：本地生成图形验证码 + Redis 保存答案</li>
 *   <li>{@code tencent}：腾讯云行为验证码，前端持 ticket/randstr 提交</li>
 * </ul>
 */
public interface CaptchaVerifier {
    void verify(CaptchaContext ctx);
}

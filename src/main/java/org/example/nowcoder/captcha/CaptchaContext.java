package org.example.nowcoder.captcha;

/**
 * 验证码校验上下文。各字段按实现的需要使用：
 * <ul>
 *   <li>Kaptcha 实现关心 {@link #kaptchaText()} 与 {@link #kaptchaOwner()}</li>
 *   <li>腾讯云验证码实现关心 {@link #tencentTicket()}、{@link #tencentRandstr()} 与 {@link #userIp()}</li>
 * </ul>
 */
public record CaptchaContext(
        String kaptchaText,
        String kaptchaOwner,
        String tencentTicket,
        String tencentRandstr,
        String userIp
) {}

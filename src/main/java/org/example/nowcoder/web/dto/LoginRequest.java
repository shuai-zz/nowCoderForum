package org.example.nowcoder.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。验证码字段按 {@code nowCoder.captcha.provider} 选择其中一组：
 * <ul>
 *   <li>{@code kaptcha}：前端传 {@link #captcha()} + {@link #captchaOwner()}</li>
 *   <li>{@code tencent}：前端传 {@link #captchaTicket()} + {@link #captchaRandstr()}</li>
 * </ul>
 */
public record LoginRequest(
        @NotBlank(message = "Username cannot be empty")
        String username,

        @NotBlank(message = "Password cannot be empty")
        String password,

        // --- Kaptcha ---
        String captcha,
        String captchaOwner,

        // --- Tencent Cloud Captcha ---
        String captchaTicket,
        String captchaRandstr,

        boolean rememberMe
) {}

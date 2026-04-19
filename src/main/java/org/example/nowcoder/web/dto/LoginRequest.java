package org.example.nowcoder.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username cannot be empty")
        String username,

        @NotBlank(message = "Password cannot be empty")
        String password,

        @NotBlank(message = "Captcha cannot be empty")
        String captcha,

        @NotBlank(message = "Captcha owner cannot be empty")
        String captchaOwner,

        boolean rememberMe
) {}
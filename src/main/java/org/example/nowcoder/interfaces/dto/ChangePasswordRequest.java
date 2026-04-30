package org.example.nowcoder.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Old password cannot be empty")
        String oldPassword,

        @NotBlank(message = "New password cannot be empty")
        @Size(min = 8, max = 64, message = "New password length must be at least 8")
        String newPassword,

        @NotBlank(message = "Confirm password cannot be empty")
        String confirmPassword
) {}

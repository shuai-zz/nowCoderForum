package org.example.nowcoder.entity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username cannot be empty")
        @Size(min = 3, max = 32, message = "Username length must be between 3 and 32")
        String username,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, max = 64, message = "Password length must be at least 8")
        String password,

        @NotBlank(message = "Confirm password cannot be empty")
        String confirmPassword,

        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Invalid email format")
        String email
) {}
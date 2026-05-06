package com.example.post.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank(message = "Title cannot be empty")
        @Size(max = 100, message = "Title length must not exceed 100")
        String title,

        @NotBlank(message = "Content cannot be empty")
        String content
) {}

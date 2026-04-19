package org.example.nowcoder.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendLetterRequest(
        @NotBlank(message = "Recipient cannot be empty")
        String toName,

        @NotBlank(message = "Content cannot be empty")
        @Size(max = 2000, message = "Content too long")
        String content
) {}

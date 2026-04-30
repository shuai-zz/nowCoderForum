package org.example.nowcoder.application.dto;

import org.example.nowcoder.domain.entity.User;

public record LoginResult(String ticket, User user) {
}
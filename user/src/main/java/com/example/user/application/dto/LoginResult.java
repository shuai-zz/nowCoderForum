package com.example.user.application.dto;


import com.example.user.domain.entity.User;

public record LoginResult(String ticket, User user) {
}
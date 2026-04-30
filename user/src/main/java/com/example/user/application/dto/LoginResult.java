package com.example.user.application.dto;


import com.example.user.domain.User;

public record LoginResult(String ticket, User user) {
}
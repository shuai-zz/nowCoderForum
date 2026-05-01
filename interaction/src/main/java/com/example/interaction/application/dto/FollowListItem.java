package com.example.interaction.application.dto;

import com.example.user.domain.User;

import java.util.Date;

public record FollowListItem(User user, Date followTime) {
}

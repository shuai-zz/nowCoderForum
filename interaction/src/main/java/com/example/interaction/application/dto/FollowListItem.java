package com.example.interaction.application.dto;

import com.example.shared.dto.AuthorRef;

import java.util.Date;

public record FollowListItem(AuthorRef user, Date followTime) {
}

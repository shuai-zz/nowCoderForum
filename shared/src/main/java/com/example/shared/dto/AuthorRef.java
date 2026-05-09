package com.example.shared.dto;

/**
 * 跨模块用户引用（防腐层ValueObject）
 * 避免 post/message/interaction 等模块直接持有 user 模块的 domain 实体
 */
public record AuthorRef(int id, String username, String avatarUrl) {
    public static AuthorRef of(int id, String username, String avatarUrl){
        return new AuthorRef(id, username, avatarUrl);
    }
}

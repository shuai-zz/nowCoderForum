package com.example.shared.dto;

/**
 * 跨模块用户引用（防腐层ValueObject）
 * 避免 post/message/interaction 等模块直接持有 user 模块的 domain 实体
 */
public record AuthorRef(int id, String username, String avatarUrl) {
    public static AuthorRef of(int id, String username, String avatarUrl){
        return new AuthorRef(id, username, avatarUrl);
    }

    /**
     * 用户已被删除时的占位引用。
     * <p>用于"内容尚在但作者账号已删"的展示场景（帖子列表、私信会话等），
     * 避免上层 stream 在 user 缺失时 NPE。
     * <p>关注/粉丝列表不该用本占位 —— 那种场景应该直接 filter 掉缺失用户。
     */
    public static AuthorRef deleted() {
        return new AuthorRef(0, "[deleted]", null);
    }
}

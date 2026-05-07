package com.example.user.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

import static com.example.shared.constant.ForumConstant.AUTHORITY_ADMIN;
import static com.example.shared.constant.ForumConstant.AUTHORITY_MODERATOR;
import static com.example.shared.constant.ForumConstant.AUTHORITY_USER;

/**
 * 集中"用户类型 → Spring Security authority"的映射规则。
 * 0=普通用户、1=管理员、2=版主，规则与 user 表字段含义对齐。
 *
 * <p>提取此工具类的目的：让 {@link UserDetailsAdapter} 与
 * {@code UserServiceImpl.getAuthorities(int)} 共用同一份规则，避免双实现漂移
 * （历史上 {@code User.getAuthorities()} 与 {@code UserServiceImpl.getAuthorities}
 * 不一致，前者漏了 MODERATOR 是 bug）。
 *
 * @author zhaoshuai
 */
public final class UserAuthorityResolver {

    private UserAuthorityResolver() {}

    public static Collection<? extends GrantedAuthority> resolve(int type) {
        return List.of(() -> switch (type) {
            case 1 -> AUTHORITY_ADMIN;
            case 2 -> AUTHORITY_MODERATOR;
            default -> AUTHORITY_USER;
        });
    }
}

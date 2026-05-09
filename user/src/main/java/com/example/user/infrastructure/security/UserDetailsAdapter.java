package com.example.user.infrastructure.security;

import com.example.user.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 把 {@link User} 适配到 Spring Security 的 {@link UserDetails} 接口。
 * <p>这一层存在的意义：把"框架视角的用户"和"领域视角的用户"分开 ——
 * domain {@code User} 是个纯 POJO，UserDetails 的契约由本类实现。
 *
 * <p>Bearer token 流程下当前用不到（{@code AuthTokenFilter} 直接用 User 当 principal），
 * 这个 Adapter 只在 {@code UserServiceImpl#loadUserByUsername(String)} 被调用 ——
 * 即将来如果接入表单登录或 Spring Security 标准认证流程时使用。
 *
 * @author zhaoshuai
 */
public final class UserDetailsAdapter implements UserDetails {

    private final User user;

    public UserDetailsAdapter(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return UserAuthorityResolver.resolve(user.getType());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == 1;
    }
}

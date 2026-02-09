package org.example.nowcoder.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author 23211
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    private int id;
    private String username;
    private String password;
    // 密码盐值，增强密码安全性
    private String salt;
    private String email;
    // 0-普通用户；1-超级管理员；2-版主
    private int type;
    private int status;
    // 账户激活码
    private String activationCode;
    private String avatarUrl;
    private Date createTime;

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add((GrantedAuthority) () -> {
            if (type == 1) {
                return "ADMIN";
            }
            return "USER";
        });
        return list;
    }

    // ture-账户未过期
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // ture-账户未锁定
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // ture-凭证未过期
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // ture-账户可用
    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}

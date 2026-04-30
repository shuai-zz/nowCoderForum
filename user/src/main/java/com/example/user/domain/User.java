package com.example.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author 23211
 */
@TableName("user")
@Builder
public class User implements UserDetails {
    @TableId(type = IdType.AUTO)
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

    public User() {
    }

    public User(int id, String username, String password, String salt, String email, int type, int status, String activationCode, String avatarUrl, Date createTime) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.salt = salt;
        this.email = email;
        this.type = type;
        this.status = status;
        this.activationCode = activationCode;
        this.avatarUrl = avatarUrl;
        this.createTime = createTime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

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
        return status==1;
    }
}

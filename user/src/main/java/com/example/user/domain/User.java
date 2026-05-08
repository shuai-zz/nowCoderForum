package com.example.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.shared.exception.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author 23211
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private int id;
    private String username;
    private String password;
    // 密码盐值，增强密码安全性
    private String salt;
    private String email;
    // 0-普通用户；1-超级管理员；2-版主
    private int type;
    // 0-未激活；1-已激活
    private int status;
    // 账户激活码
    private String activationCode;
    private String avatarUrl;
    private Date createTime;

    // 用户类型
    public static final int TYPE_USER = 0;
    public static final int TYPE_ADMIN = 1;
    public static final int TYPE_MODERATOR = 2;
    public static final int STATUS_INACTIVE = 0;
    public static final int STATUS_ACTIVATED = 1;

    public boolean isActivated() {
        return this.status == STATUS_ACTIVATED;
    }

    public boolean canActivateWith(String code) {
        return !isActivated() && activationCode.equals(code);
    }

    public void activate() {
        if (isActivated()) {
            throw new ValidationException("Account already activated");
        }
        this.status = STATUS_ACTIVATED;
    }

    public boolean isAdmin() {
        return this.type == TYPE_ADMIN;
    }

    public boolean isModerator() {
        return this.type == TYPE_MODERATOR;
    }

    public boolean canTopOrFeature() {
        return isAdmin() || isModerator();
    }
}

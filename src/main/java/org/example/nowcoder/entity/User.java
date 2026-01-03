package org.example.nowcoder.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author 23211
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private int id;
    private String username;
    private String password;
    // 密码盐值，增强密码安全性
    private String salt;
    private String email;
    private int type;
    private int status;
    // 账户激活码
    private String activationCode;
    private String avatarUrl;
    private Date createTime;
}

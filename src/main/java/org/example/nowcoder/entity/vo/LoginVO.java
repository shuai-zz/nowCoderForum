package org.example.nowcoder.entity.vo;

/**
 * 登录成功响应。前端持有 ticket 并通过 Authorization: Bearer &lt;ticket&gt; 发送后续请求。
 */
public record LoginVO(
        String ticket,
        int expiresIn,
        UserVO user
) {}
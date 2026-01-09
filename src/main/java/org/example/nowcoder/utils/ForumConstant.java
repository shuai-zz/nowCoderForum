package org.example.nowcoder.utils;

/**
 * @author 23211
 */
public interface ForumConstant {
    // 激活成功
    int ACTIVATION_SUCCESS = 0;
    // 重复激活
    int ACTIVATION_REPEAT = 1;
    // 激活失败
    int ACTIVATION_FAILURE = 2;

    // 默认过期时间
    int DEFAULT_EXPIRED_SECONDS = 3600 * 12;
    // remember me expired time
    int REMEMBER_EXPIRED_SECONDS = 3600 * 24 * 100;

    // 实体类型
    //post
    int ENTITY_TYPE_POST = 1;
    //comment
    int ENTITY_TYPE_COMMENT = 2;
    //user
    int ENTITY_TYPE_USER = 3;
}

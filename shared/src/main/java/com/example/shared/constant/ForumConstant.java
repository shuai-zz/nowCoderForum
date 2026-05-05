package com.example.shared.constant;


/**
 * @author 23211
 */
public final class ForumConstant {

    private ForumConstant() {
    }

    // 激活成功
    public static final int ACTIVATION_SUCCESS = 0;
    // 重复激活
    public static final int ACTIVATION_REPEAT = 1;
    // 激活失败
    public static final int ACTIVATION_FAILURE = 2;

    // 默认过期时间
    public static final int DEFAULT_EXPIRED_SECONDS = 3600 * 12;
    // remember me expired time
    public static final int REMEMBER_EXPIRED_SECONDS = 3600 * 24 * 100;

    // 实体类型
    // post
    public static final int ENTITY_TYPE_POST = 1;
    // comment
    public static final int ENTITY_TYPE_COMMENT = 2;
    // user
    public static final int ENTITY_TYPE_USER = 3;

    public static final String TOPIC_COMMENT = "comment";
    public static final String TOPIC_LIKE = "like";
    public static final String TOPIC_FOLLOW = "follow";
    public static final String TOPIC_PUBLISH = "publish";
    public static final String TOPIC_DELETE = "delete";

    public static final int SYSTEM_USER_ID = 1;

    public static final String AUTHORITY_USER = "user";
    public static final String AUTHORITY_ADMIN = "admin";
    public static final String AUTHORITY_MODERATOR = "moderator";
}

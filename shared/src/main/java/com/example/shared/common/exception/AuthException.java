package com.example.shared.common.exception;

/** 认证 / 鉴权失败。
 * @author zhaoshuai*/
public final class AuthException extends BizException {

    public AuthException(String msg) {
        super(401, msg);
    }

    @Override
    public int status() {
        return 401;
    }
}
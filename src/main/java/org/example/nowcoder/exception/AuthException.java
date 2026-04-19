package org.example.nowcoder.exception;

/** 认证 / 鉴权失败。 */
public final class AuthException extends BizException {

    public AuthException(String msg) {
        super(401, msg);
    }

    @Override
    public int status() {
        return 401;
    }
}
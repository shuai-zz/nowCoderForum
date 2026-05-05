package com.example.shared.exception;

/** 参数 / 业务规则校验失败。
 * @author zhaoshuai*/
public final class ValidationException extends BizException {

    public ValidationException(String msg) {
        super(422, msg);
    }

    @Override
    public int status() {
        return 422;
    }
}
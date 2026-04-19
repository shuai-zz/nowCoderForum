package org.example.nowcoder.exception;

/** 参数 / 业务规则校验失败。 */
public final class ValidationException extends BizException {

    public ValidationException(String msg) {
        super(422, msg);
    }

    @Override
    public int status() {
        return 422;
    }
}
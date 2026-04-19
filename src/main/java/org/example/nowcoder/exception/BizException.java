package org.example.nowcoder.exception;

/**
 * 业务异常基类（sealed，限定了所有业务异常种类）。
 * 新增业务异常类型时需在 permits 列表里登记。
 */
public sealed class BizException extends RuntimeException
        permits AuthException, ResourceNotFoundException, ValidationException {

    private final int code;

    protected BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    protected BizException(int code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 对应的 HTTP 状态码。子类可覆盖。 */
    public int status() {
        return 400;
    }
}
package org.example.nowcoder.controller.common;

/**
 * 统一 API 响应体。
 * <p>code=0 表示成功；非 0 表示业务错误，具体语义由异常类型决定。
 */
public record Result<T>(int code, String msg, T data) {

    public static final int CODE_OK = 0;

    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_OK, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(CODE_OK, "ok", null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
package org.example.nowcoder.exception;

/** 查询的资源不存在。 */
public final class ResourceNotFoundException extends BizException {

    public ResourceNotFoundException(String msg) {
        super(404, msg);
    }

    @Override
    public int status() {
        return 404;
    }
}
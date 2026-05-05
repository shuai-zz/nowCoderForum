package com.example.shared.exception;

/** 查询的资源不存在。
 * @author zhaoshuai*/
public final class ResourceNotFoundException extends BizException {

    public ResourceNotFoundException(String msg) {
        super(404, msg);
    }

    @Override
    public int status() {
        return 404;
    }
}
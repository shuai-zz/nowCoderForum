package com.example.shared.exception;

public final class UploadFailedException extends BizException {


    public UploadFailedException(String msg, Throwable cause) {
        super(500, msg, cause);
    }


    @Override
    public int status() {
        return 500;
    }

}

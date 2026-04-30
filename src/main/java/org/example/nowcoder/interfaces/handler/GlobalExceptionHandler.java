package org.example.nowcoder.interfaces.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.exception.BizException;
import org.example.nowcoder.interfaces.common.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * REST API 全局异常处理。
 * <p>Order 设为高优先级，保证业务异常优先被捕获。
 * @author zhaoshuai
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
@Order(0)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("BizException: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.status()).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorMsg)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(400, msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(this::fieldErrorMsg)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(400, msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAll(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError().body(Result.fail(500, "Internal server error"));
    }

    private String fieldErrorMsg(FieldError err) {
        return err.getField() + ": " + err.getDefaultMessage();
    }
}
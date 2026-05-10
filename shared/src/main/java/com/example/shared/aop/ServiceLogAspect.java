package com.example.shared.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author zhaoshuai
 */
@Component
@Aspect
@Slf4j
public class ServiceLogAspect {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Pointcut("execution(* com.example.*.application.service.*.*(..))")
    public void pointcut(){}

    @Before("pointcut()")
    public void before(JoinPoint joinPoint){
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String ip=null;
        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            ip = request.getRemoteHost();
        }
        String now = LocalDateTime.now().format(FMT);

        String target = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();

        log.info("User [{}] accessed [{}] at [{}]", ip, target, now);
    }
}

package org.example.nowcoder.controller.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.utils.ForumUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author zhaoshuai
 */
@ControllerAdvice(annotations = Controller.class)
@Slf4j
public class ExceptionAdvice {

    @ExceptionHandler({Exception.class})
    public void handleException(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {

        log.error("Server exception occurred: {}", e.getMessage());
        for(StackTraceElement element: e.getStackTrace()){
            log.error(element.toString());
        }

        String xRequestedWith = request.getHeader("x-requested-with");
        if("XMLHttpRequest".equals(xRequestedWith)){
            response.setContentType("application/plain");
            PrintWriter writer = response.getWriter();
            writer.write(ForumUtil.getJsonString(1, "Server exception"));
        }else{
            response.sendRedirect(request.getContextPath()+"/error");
        }
    }
}

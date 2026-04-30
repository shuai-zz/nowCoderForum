package org.example.nowcoder.infrastructure.util;

import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author zhaoshuai
 */
@Component
@Deprecated
public class ThymeleafDateFormatter {
    public String format(Date date, String pattern){
        if(date==null) {
            return "";
        }
        return new SimpleDateFormat(pattern).format(date);
    }
}

package org.example.nowcoder.utils;

import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author zhaoshuai
 */
@Component
public class ThymeleafDateFormatter {
    public String format(Date date, String pattern){
        if(date==null) {
            return "";
        }
        return new SimpleDateFormat(pattern).format(date);
    }
}

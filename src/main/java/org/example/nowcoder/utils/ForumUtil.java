package org.example.nowcoder.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import java.util.UUID;

/**
 * @author 23211
 */
public class ForumUtil {
    // UUID 生成
    public static String generateUuid() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    // md5
    public static String md5(String key) {
        if(StringUtils.isBlank(key)) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(key.getBytes());
    }
}

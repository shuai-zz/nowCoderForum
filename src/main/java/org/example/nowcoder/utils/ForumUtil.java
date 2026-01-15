package org.example.nowcoder.utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import java.util.Map;
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


    public static String getJsonString(int code, String msg, Map<String,Object> map) {
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        if(map != null) {
            json.put("map", map);
        }
        return json.toJSONString();
    }
    public static String getJsonString(int code, String msg) {
        return getJsonString(code, msg, null);
    }
    public static String getJsonString(int code) {
        return getJsonString(code, null, null);
    }
}

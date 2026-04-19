package org.example.nowcoder.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ForumUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String generateUuid() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static String md5(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(key.getBytes());
    }

    /**
     * 老代码的 JSON 响应格式，仅供未迁移的 Thymeleaf/AJAX 端点使用。
     * 新 REST API 统一使用 {@code Result<T>}。
     */
    public static String getJsonString(int code, String msg, Map<String, Object> map) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("code", code);
        obj.put("msg", msg);
        if (map != null) obj.put("map", map);
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON response", e);
        }
    }

    public static String getJsonString(int code, String msg) {
        return getJsonString(code, msg, null);
    }

    public static String getJsonString(int code) {
        return getJsonString(code, null, null);
    }
}

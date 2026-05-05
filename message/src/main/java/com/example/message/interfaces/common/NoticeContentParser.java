package com.example.message.interfaces.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

/**
 * 统一解析 Message.content 中存放的 JSON 元数据。
 * 典型字段：userId（触发者）、entityType、entityId、postId。
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
public class NoticeContentParser {

    private final ObjectMapper objectMapper;

    public Map<String, Object> parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Map.of();
        }
        String unescaped = HtmlUtils.htmlUnescape(rawContent);
        try {
            return objectMapper.readValue(unescaped, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse notice content: " + unescaped, e);
        }
    }
}
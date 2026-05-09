package com.example.shared.domain;

import com.example.shared.utils.SensitiveFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 净化用户产出内容：HTML转义+敏感词过滤
 */
@Component
@RequiredArgsConstructor
public class ContentSanitizer {
    private final SensitiveFilter sensitiveFilter;

    public String sanitize(String raw){
        if(raw==null||raw.isBlank()){
            return raw;
        }
        String escaped = HtmlUtils.htmlEscape(raw);
        return sensitiveFilter.filter(escaped);
    }
}

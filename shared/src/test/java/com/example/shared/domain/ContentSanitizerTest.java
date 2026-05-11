package com.example.shared.domain;

import com.example.shared.utils.SensitiveFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSanitizerTest {

    private static ContentSanitizer sanitizer;

    @BeforeAll
    static void setUp() {
        SensitiveFilter filter = new SensitiveFilter();
        filter.init();
        sanitizer = new ContentSanitizer(filter);
    }

    @Test
    void nullReturnsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void blankReturnsAsIs() {
        // 空串/空白直接 return raw — 不进过滤器
        assertThat(sanitizer.sanitize("")).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEqualTo("   ");
    }

    @Test
    void htmlEntitiesAreEscaped() {
        String out = sanitizer.sanitize("<script>alert(1)</script>");
        assertThat(out).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void htmlEscapeHappensBeforeSensitiveFilter() {
        // ampersand 先转 &amp;，结果不该再出现裸 &
        String out = sanitizer.sanitize("Tom & Jerry");
        assertThat(out).isEqualTo("Tom &amp; Jerry");
    }

    @Test
    void sensitiveWordsAreFilteredAfterEscape() {
        String out = sanitizer.sanitize("fuck <b>");
        assertThat(out).contains("***");
        assertThat(out).contains("&lt;b&gt;");
    }

    @Test
    void plainTextPassesThroughUnchanged() {
        assertThat(sanitizer.sanitize("hello world 你好")).isEqualTo("hello world 你好");
    }
}

package com.example.shared.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveFilterTest {

    private static SensitiveFilter filter;

    @BeforeAll
    static void setUp() {
        // @PostConstruct 不会被自动调用，手动 init 加载 sensitive-words.txt（在 classpath 中）
        filter = new SensitiveFilter();
        filter.init();
    }

    @Test
    void blankReturnsNull() {
        // 实现里 isBlank 返回 null（这是约定，不要悄悄改）
        assertThat(filter.filter(null)).isNull();
        assertThat(filter.filter("")).isNull();
        assertThat(filter.filter("   ")).isNull();
    }

    @Test
    void cleanTextIsReturnedAsIs() {
        assertThat(filter.filter("hello world")).isEqualTo("hello world");
    }

    @Test
    void englishSensitiveWordIsReplaced() {
        // sensitive-words.txt 包含 "fuck"
        assertThat(filter.filter("fuck you")).isEqualTo("*** you");
    }

    @Test
    void chineseSensitiveWordIsReplaced() {
        // sensitive-words.txt 包含 "赌博"
        assertThat(filter.filter("禁止赌博活动")).isEqualTo("禁止***活动");
    }

    @Test
    void caseInsensitiveMatch() {
        assertThat(filter.filter("FUCK")).isEqualTo("***");
        assertThat(filter.filter("Fuck")).isEqualTo("***");
    }

    @Test
    void symbolsBetweenSensitiveCharsStillMatch() {
        // 实现按 symbol 跳过：f*u*c*k 仍然命中 fuck
        assertThat(filter.filter("f*u*c*k off")).isEqualTo("*** off");
    }

    @Test
    void multipleOccurrencesAllReplaced() {
        assertThat(filter.filter("fuck and fuck")).isEqualTo("*** and ***");
    }

    @Test
    void leadingSymbolsArePreserved() {
        // 符号在中间但不在敏感词内部时被原样输出
        assertThat(filter.filter("--- hello")).isEqualTo("--- hello");
    }
}

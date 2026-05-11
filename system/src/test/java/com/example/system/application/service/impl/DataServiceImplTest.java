package com.example.system.application.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private DataServiceImpl service;

    private static Date asDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @SuppressWarnings("unchecked")
    private HyperLogLogOperations<String, Object> stubHll() {
        HyperLogLogOperations<String, Object> hll = mock(HyperLogLogOperations.class);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hll);
        return hll;
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> stubValue() {
        ValueOperations<String, Object> v = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(v);
        return v;
    }

    // ---------- recordUv ----------

    @Test
    void recordUvAddsIpToTodayHyperLogLog() {
        HyperLogLogOperations<String, Object> hll = stubHll();

        service.recordUv("1.2.3.4");

        String expectedKey = "uv:" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        verify(hll).add(expectedKey, "1.2.3.4");
    }

    // ---------- calculateUv ----------

    @Test
    void calculateUvNullStartThrows() {
        assertThatThrownBy(() -> service.calculateUv(null, asDate(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculateUvNullEndThrows() {
        assertThatThrownBy(() -> service.calculateUv(asDate(LocalDate.of(2026, 1, 1)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculateUvUnionsPerDayKeysAndAppliesTenMinuteTtl() {
        // 3 天区间：union 应收到 3 个日 key，merged key 应设置 10 分钟 TTL
        HyperLogLogOperations<String, Object> hll = stubHll();
        Date start = asDate(LocalDate.of(2026, 5, 1));
        Date end = asDate(LocalDate.of(2026, 5, 3));
        when(hll.size(any(String[].class))).thenReturn(42L);

        long uv = service.calculateUv(start, end);

        assertThat(uv).isEqualTo(42L);

        ArgumentCaptor<String> mergedKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> daysCaptor = ArgumentCaptor.forClass(String[].class);
        verify(hll).union(mergedKeyCaptor.capture(), daysCaptor.capture());

        assertThat(mergedKeyCaptor.getValue()).isEqualTo("uv:20260501:20260503");
        assertThat(daysCaptor.getValue()).containsExactly(
                "uv:20260501", "uv:20260502", "uv:20260503"
        );

        // S2 修复 —— merged key 必须带 TTL，否则每个不同区间生成新 key 永久堆积
        verify(redisTemplate).expire(eq("uv:20260501:20260503"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void calculateUvSingleDayRangeYieldsSingleKey() {
        HyperLogLogOperations<String, Object> hll = stubHll();
        Date date = asDate(LocalDate.of(2026, 5, 1));
        when(hll.size(any(String[].class))).thenReturn(1L);

        service.calculateUv(date, date);

        ArgumentCaptor<String[]> daysCaptor = ArgumentCaptor.forClass(String[].class);
        verify(hll).union(any(String.class), daysCaptor.capture());
        assertThat(daysCaptor.getValue()).containsExactly("uv:20260501");
    }

    // ---------- recordDau ----------

    @Test
    void recordDauSetsBitForUserIdOnTodayBitmap() {
        ValueOperations<String, Object> valueOps = stubValue();

        service.recordDau(123);

        String expectedKey = "dau:" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        verify(valueOps).setBit(expectedKey, 123, true);
    }

    // ---------- calculateDau ----------

    @Test
    void calculateDauNullStartThrows() {
        assertThatThrownBy(() -> service.calculateDau(null, asDate(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculateDauAppliesTenMinuteTtlToMergedKey() {
        Date start = asDate(LocalDate.of(2026, 5, 1));
        Date end = asDate(LocalDate.of(2026, 5, 2));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(99L);

        long dau = service.calculateDau(start, end);

        assertThat(dau).isEqualTo(99L);
        verify(redisTemplate).expire(eq("dau:20260501:20260502"), eq(Duration.ofMinutes(10)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculateDauReturnsZeroWhenCallbackReturnsNull() {
        // 防御：BITOP/BITCOUNT 返回 null 不应 NPE
        Date date = asDate(LocalDate.of(2026, 5, 1));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        assertThat(service.calculateDau(date, date)).isZero();
    }
}

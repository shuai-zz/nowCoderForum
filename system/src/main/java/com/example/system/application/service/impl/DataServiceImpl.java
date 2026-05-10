package com.example.system.application.service.impl;

import com.example.shared.utils.RedisKeyUtil;
import com.example.system.application.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {
    private final RedisTemplate<String, Object> redisTemplate;
    /**
     * {@code DateTimeFormatter} 是不可变 + 线程安全的，可以作为 static final 共享；
     * {@code SimpleDateFormat} 不行（内部持可变 Calendar 状态），并发下会写出脏 Redis key。
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * UV/DAU 区间合并 key 的 TTL。每次区间不同就生成新 key，没 TTL 永久堆积。
     * 10 分钟足够 admin 面板展示完毕，过期后 redis 自动回收。
     */
    private static final Duration MERGED_KEY_TTL = Duration.ofMinutes(10);

    //记录UV数据，记录指定IP
    @Override
    public void recordUv(String ip) {
        String redisKey = RedisKeyUtil.getUvKey(today());
        redisTemplate.opsForHyperLogLog().add(redisKey, ip);
    }

    // 统计指定时间段内UV
    @Override
    public long calculateUv(Date start, Date end) {
        if (end == null || start == null) {
            throw new IllegalArgumentException("Parameter cannot be null");
        }
        // 整理key
        List<String> keyList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        while (!calendar.getTime().after(end)) {
            String key = RedisKeyUtil.getUvKey(formatDate(calendar.getTime()));
            keyList.add(key);
            calendar.add(Calendar.DATE, 1);
        }
        // 合并数据
        String redisKey = RedisKeyUtil.getUvKey(formatDate(start), formatDate(end));
        redisTemplate.opsForHyperLogLog().union(redisKey, keyList.toArray(new String[0]));
        redisTemplate.expire(redisKey, MERGED_KEY_TTL);
        return redisTemplate.opsForHyperLogLog().size(redisKey);
    }

    // 记录指定用户到DAU
    @Override
    public void recordDau(int userId) {
        String redisKey = RedisKeyUtil.getDauKey(today());
        redisTemplate.opsForValue().setBit(redisKey, userId, true);
    }

    // 统计指定时间段内DAU
    @Override
    public long calculateDau(Date start, Date end) {
        if (end == null || start == null) {
            throw new IllegalArgumentException("Parameter cannot be null");
        }
        // 整理key
        List<byte[]> keyList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        while (!calendar.getTime().after(end)) {
            String key = RedisKeyUtil.getDauKey(formatDate(calendar.getTime()));
            keyList.add(key.getBytes());
            calendar.add(Calendar.DATE, 1);
        }

        String mergedKey = RedisKeyUtil.getDauKey(formatDate(start), formatDate(end));
        Long result = redisTemplate.execute((RedisCallback<Long>) connection -> {
            connection.stringCommands().bitOp(RedisConnection.BitOperation.OR, mergedKey.getBytes(), keyList.toArray(new byte[0][0]));
            return connection.stringCommands().bitCount(mergedKey.getBytes());
        });
        redisTemplate.expire(mergedKey, MERGED_KEY_TTL);
        return result != null ? result : 0L;
    }

    private static String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    private static String formatDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT);
    }
}

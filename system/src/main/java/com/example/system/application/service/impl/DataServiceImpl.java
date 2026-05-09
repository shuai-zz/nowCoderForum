package com.example.system.application.service.impl;

import com.example.shared.utils.RedisKeyUtil;
import com.example.system.application.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
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
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    //记录UV数据，记录指定IP
    @Override
    public void recordUv(String ip) {
        String redisKey = RedisKeyUtil.getUvKey(dateFormat.format(new Date()));
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
            String key = RedisKeyUtil.getUvKey(dateFormat.format(calendar.getTime()));
            keyList.add(key);
            calendar.add(Calendar.DATE, 1);
        }
        // 合并数据
        String redisKey = RedisKeyUtil.getUvKey(dateFormat.format(start), dateFormat.format(end));
        redisTemplate.opsForHyperLogLog().union(redisKey, keyList.toArray(new String[0]));
        return redisTemplate.opsForHyperLogLog().size(redisKey);
    }

    // 记录指定用户到DAU
    @Override
    public void recordDau(int userId) {
        String redisKey = RedisKeyUtil.getDauKey(dateFormat.format(new Date()));
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
            String key = RedisKeyUtil.getDauKey(dateFormat.format(calendar.getTime()));
            keyList.add(key.getBytes());
            calendar.add(Calendar.DATE, 1);
        }

        Long result = redisTemplate.execute((RedisCallback<Long>) connection -> {
            String redisKey = RedisKeyUtil.getDauKey(dateFormat.format(start), dateFormat.format(end));
            connection.stringCommands().bitOp(RedisConnection.BitOperation.OR, redisKey.getBytes(), keyList.toArray(new byte[0][0]));
            return connection.stringCommands().bitCount(redisKey.getBytes());
        });
        return result != null ? result : 0L;
    }
}

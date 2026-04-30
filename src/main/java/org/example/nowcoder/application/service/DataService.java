package org.example.nowcoder.application.service;

import org.apache.kafka.common.protocol.types.Field;

import java.util.Date;

/**
 * @author zhaoshuai
 */
public interface DataService {
    void recordUv(String ip);
    long calculateUv(Date start, Date end);
    void recordDau(int userId);
    long calculateDau(Date start, Date end);
}

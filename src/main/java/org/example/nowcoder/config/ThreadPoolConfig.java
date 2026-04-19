package org.example.nowcoder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author zhaoshuai
 */
@Configuration
@EnableScheduling
@EnableAsync
public class ThreadPoolConfig {
}

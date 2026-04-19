package org.example.nowcoder;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class ThreadPoolTest {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolTest.class);
    // JDK普通线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    // JDK可执行定时任务的线程池
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

    // Spring线程池
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    // spring 可执行定时任务的线程池
    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    private void sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            logger.atDebug().log("Sleep error");
        }
    }


    @Test
    public void testExecutorService(){
        Runnable task= () -> {
            logger.warn("hello, executeService");
        };
        for (int i = 0; i < 10; i++) {
            executorService.submit(task);
        }
        sleep(10000);
    }

    @Test
    public void testScheduledExecutorService(){
        Runnable task= () -> {
            logger.warn("hello, scheduledExecutorService, I'm Thread:{}",Thread.currentThread().getName());
        };
        scheduledExecutorService.scheduleAtFixedRate(task, 10_000,1000, TimeUnit.MILLISECONDS);
        sleep(30_000);

    }

    @Test
    public void testThreadPoolTaskExecutor() {
        Runnable task = () -> {
            logger.warn("hello, threadPoolTaskExecutor, I'm Thread:{}", Thread.currentThread().getName());
        };
        for (int i = 0; i < 10; i++) {
            threadPoolTaskExecutor.execute(task);
        }
    }

    @Test
    public void testThreadPoolTaskScheduler() {
        Runnable task = () -> {
            logger.warn("hello, threadPoolTaskScheduler, I'm Thread:{}", Thread.currentThread().getName());
        };


        threadPoolTaskScheduler.scheduleAtFixedRate(task, Duration.ofMillis(1_000));
        sleep(30_000);
    }

}

package com.example.user.application.service.impl;

import com.example.user.application.service.KaptchaService;
import com.google.code.kaptcha.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KaptchaServiceImplTest {

    @Mock
    private Producer kaptchaProducer;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private KaptchaServiceImpl service;

    @BeforeEach
    void wireRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void issueReturnsKaptchaImageAndUniqueOwner() {
        BufferedImage img = new BufferedImage(80, 30, BufferedImage.TYPE_INT_RGB);
        when(kaptchaProducer.createText()).thenReturn("ABCD");
        when(kaptchaProducer.createImage("ABCD")).thenReturn(img);

        KaptchaService.Captcha captcha = service.issue();

        assertThat(captcha.image()).isSameAs(img);
        assertThat(captcha.owner()).isNotBlank();
    }

    @Test
    void issueWritesAnswerToRedisWithKaptchaKeyAndSixtySecondTtl() {
        when(kaptchaProducer.createText()).thenReturn("ZZZZ");
        when(kaptchaProducer.createImage(anyString())).thenReturn(new BufferedImage(1, 1, 1));

        KaptchaService.Captcha captcha = service.issue();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture(), any(TimeUnit.class));

        assertThat(keyCaptor.getValue()).isEqualTo("kaptcha:" + captcha.owner());
        assertThat(valueCaptor.getValue()).isEqualTo("ZZZZ");
        assertThat(ttlCaptor.getValue()).isEqualTo(60L);
    }

    @Test
    void issueUsesSecondsAsTtlUnit() {
        when(kaptchaProducer.createText()).thenReturn("X");
        when(kaptchaProducer.createImage(anyString())).thenReturn(new BufferedImage(1, 1, 1));

        service.issue();

        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(valueOps).set(anyString(), anyString(), anyLong(), unitCaptor.capture());
        assertThat(unitCaptor.getValue()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void successiveIssuesYieldDifferentOwners() {
        when(kaptchaProducer.createText()).thenReturn("A").thenReturn("B");
        when(kaptchaProducer.createImage(anyString())).thenReturn(new BufferedImage(1, 1, 1));

        KaptchaService.Captcha first = service.issue();
        KaptchaService.Captcha second = service.issue();

        assertThat(first.owner()).isNotEqualTo(second.owner());
    }
}

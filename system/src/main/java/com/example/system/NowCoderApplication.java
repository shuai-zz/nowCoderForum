package com.example.system;

import jakarta.annotation.PostConstruct;
import com.example.system.infrastructure.captcha.CaptchaProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@SpringBootApplication(scanBasePackages = "com.example")
@EnableConfigurationProperties(CaptchaProperties.class)
@MapperScan({"com.example.user.infrastructure.mapper", "com.example.post.infrastructure.mapper", "com.example.interaction.infrastructure.mapper", "com.example.message.infrastructure.mapper"})
@EnableElasticsearchRepositories("com.example.search.infrastructure.repository")
public class NowCoderApplication {

    @PostConstruct
    public void init() {
        // 解决netty启动冲突问题
        // see Netty4Utils.setAvailableProcessors()
        System.setProperty("es.set.netty.runtime.available.processors", "false");
    }

    public static void main(String[] args) {
        SpringApplication.run(NowCoderApplication.class, args);
    }
}

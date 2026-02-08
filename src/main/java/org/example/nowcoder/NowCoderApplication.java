package org.example.nowcoder;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 23211
 */
@SpringBootApplication
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

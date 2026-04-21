package org.example.nowcoder;

import org.example.nowcoder.utils.MailClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("real smtp — run manually")
public class MailTest {
    @Autowired
    private MailClient mailClient;

    @Test
    public void mailTest() {
        mailClient.sendMail("21080104@mail.ecust.edu.cn", "测试", "spring mail test");
    }
}

package org.example.nowcoder;

import org.example.nowcoder.utils.MailClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

//@SpringBootTest(properties={
//        "spring.mail.username=2321136573@qq.com",
//        "spring.mail.password=awxhowpkbiwfeabc"
//})
@SpringBootTest
public class MailTest {
    @Autowired
    private MailClient mailClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    public void mailTest() {
        mailClient.sendMail("21080104@mail.ecust.edu.cn", "测试", "spring mail test");
    }


    @Test
    @Disabled("HTML 邮件")
    public void htmlMailTest(){
        Context context = new Context();
        context.setVariable("username", "张三");

        String content=templateEngine.process("/mail/demo", context);
        System.out.println(content);

        mailClient.sendMail("21080104@mail.ecust.edu.cn", "HTML 邮件", content);
    }

}

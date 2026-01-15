package org.example.nowcoder;

import org.example.nowcoder.utils.SensitiveFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SensitiveWordTest {
    @Autowired
    private SensitiveFilter sensitiveFilter;
    @Test
    public void testSensitiveWord() {
        String text = "you are a fucking DICK head, 赌博，开票";
        String filterText = "you are a ***ing *** head, ***，***";
//        System.out.println(sensitiveFilter.filter(text));
        Assertions.assertEquals(filterText, sensitiveFilter.filter(text));
    }
}

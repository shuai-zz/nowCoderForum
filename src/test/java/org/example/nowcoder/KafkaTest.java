//package org.example.nowcoder;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.stereotype.Component;
//import org.springframework.test.context.ContextConfiguration;
//
//@SpringBootTest
//public class KafkaTest {
//    @Autowired
//    private KafkaProducer kafkaProducer;
//
//    @Test
//    public void testKafka(){
//        kafkaProducer.sendMessage("test", "hello kafka");
//        kafkaProducer.sendMessage("test", "you there?");
//        try {
//            Thread.sleep(1000*10);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
//@Component
//class KafkaConsumer {
//    @KafkaListener(topics = {"test"})
//    public void onMessage(String message) {
//        System.out.println("receive:" + message);
//    }
//}
//@Component
//class KafkaProducer {
//    @Autowired
//    private KafkaTemplate kafkaTemplate;
//
//    public void sendMessage(String topic, String message) {
//        kafkaTemplate.send(topic, message);
//    }
//
//}

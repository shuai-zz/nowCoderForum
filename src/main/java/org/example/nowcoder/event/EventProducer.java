package org.example.nowcoder.event;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
public class EventProducer {
    private final KafkaTemplate<String,String> kafkaTemplate;

    public void fireEvent(Event event){
        kafkaTemplate.send(event.getTopic(), JSONObject.toJSONString(event));
    }

}

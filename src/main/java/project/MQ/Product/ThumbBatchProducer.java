package project.MQ.Product;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.MQ.Event.ThumbEvent;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Slf4j
public class ThumbBatchProducer {

    private static final ConcurrentLinkedQueue<ThumbEvent> EVENT_QUEUE = new ConcurrentLinkedQueue<>();

    private final Object lock = new Object();

    @Value("${rocketmq.producer.batch-send-size}")
    private int batchSendSize;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public void addThumb(ThumbEvent thumbEvent) {
        if (thumbEvent == null) {
            return;
        }
        synchronized (lock) {
            EVENT_QUEUE.offer(thumbEvent);
            if (EVENT_QUEUE.size() >= batchSendSize) {
                batchSendThumb();
            }
        }
    }

    @Scheduled(fixedDelayString = "${rocketmq.producer.batch-send-interval}")
    public void scheduledBatchSendThumb() {
        synchronized (lock) {
            if (EVENT_QUEUE.isEmpty()) {
                return;
            }
            batchSendThumb();
        }
    }

    public void batchSendThumb() {
        List<ThumbEvent> toSend = new ArrayList<>();
        while (toSend.size() < batchSendSize) {
            ThumbEvent event = EVENT_QUEUE.poll();
            if (event == null) {
                break;
            }
            toSend.add(event);
        }
        if (toSend.isEmpty()) {
            return;
        }
        try {
            rocketMQTemplate.syncSend("THUMB_TOPIC", toSend, 3000);
            log.info("sent thumb batch, size={}", toSend.size());
        } catch (Exception e) {
            EVENT_QUEUE.addAll(toSend);
            log.warn("failed to send thumb batch", e);
        }
    }
}

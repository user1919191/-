package project.MQ.Product;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.MQ.Consumer.ThumbConsumer;
import project.MQ.Event.ThumbEvent;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 点赞事件生产者
 */

@Component
@ConfigurationProperties("rocketmq")
@Slf4j
public class ThumbBatchProducer {

    /**
     * 消息队列常量
     */
    @Value("${rocketmq.producer.batch-max-size}")
    private int max_size;

    @Value("${rocketmq.producer.batch-send-delay-ms}")
    private static int delay_ms;

    /**
     * 消息暂存队列
     */
    private final ConcurrentLinkedQueue<ThumbEvent> eventQueue = new ConcurrentLinkedQueue<>();

    /**
     * 执行锁
     * @param thumbEvent
     */
    private Object lock = new Object();

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 添加事件到消息队列
     * @param thumbEvent
     */
    public void AddThumb(ThumbEvent thumbEvent){
        synchronized (lock){
            boolean add = eventQueue.add(thumbEvent);
            if (add){
                if(eventQueue.size() >= max_size){
                    batchSendThumb();
                }
            }
        }
    }

    /**
     * 定时批量发送消息
     */
    @Scheduled(fixedDelayString = "${rocketmq.producer.batch-send-delay-ms}")
    public void scheduledBatchSendThumb(){
        log.info("执行定时发送任务!");
        synchronized (lock){
            if(eventQueue.isEmpty()) return;
            batchSendThumb();
        }
    }

    /**
     * 批量发送消息
     */
    public void batchSendThumb(){
        List<ThumbEvent> toSend;
        synchronized (lock){
            toSend = eventQueue.stream().limit(max_size).collect(Collectors.toList());
            eventQueue.clear();
            log.info("队列发送成功{}",toSend.size());
        }
        try {
            rocketMQTemplate.syncSend("THUMB_TOPIC",toSend,3000);
        }catch (Exception e){
            synchronized (lock) {
                eventQueue.addAll(toSend);
            }
            log.warn("ErrorCode.OPERATION_ERROR,{}",e.getMessage());
        }
    }
}

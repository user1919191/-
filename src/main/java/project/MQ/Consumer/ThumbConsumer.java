package project.MQ.Consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.MQ.Event.ThumbEvent;
import project.model.entity.PostThumb;
import project.service.PostThumbService;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 点赞消息消费者
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "THUMB_TOPIC",
        consumerGroup = "thumb-consumer-group",
        consumeThreadMax = 20,
        consumeTimeout = 30000L
)
public class ThumbConsumer implements RocketMQListener<List<ThumbEvent>> {

    /**
     * 重试消息队列
     */
    private static final ConcurrentLinkedQueue eventQueue = new ConcurrentLinkedQueue();

    @Resource
    private PostThumbService postThumbService;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.consumer.max-reconsume-times}")
    private int retry_time;

    /**
     * 接收消息
     * @param events
     */
    @Override
    public void onMessage(List<ThumbEvent> events) {
        log.info("消费者接收到消息,准备执行");
        //1.按postID分组处理
        Set<ThumbEvent> thumbEvents = events.stream()
                .collect(Collectors.toMap(
                        e -> e.getUserid() + "_" + e.getPostId(),
                        e -> e,
                        (existing, replacement) ->
                                existing.getDateTime().isAfter(replacement.getDateTime()) ? existing : replacement
                ))
                .values().stream()
                .collect(Collectors.toSet());
        //2.处理事件
        thumbEvents.forEach(this::processEventGroup);
    }

    /**
     * 根据帖子ID处理消息
     * @param event
     */
    private void processEventGroup(ThumbEvent event) {
        log.info("正在向数据库插入数据");
        try {
            PostThumb postThumb = new PostThumb();
            BeanUtils.copyProperties(event, postThumb);
            postThumb.setUpdateTime(new Date());
            if(event.getIsThumb().equals(ThumbEvent.ThumbType.THUMB)){
                postThumb.setIsThumb(1);
            }else{
                postThumb.setIsThumb(0);
            }
            LambdaQueryWrapper<PostThumb> queryWrapper = Wrappers.lambdaQuery(PostThumb.class)
                    .eq(PostThumb::getPostId, event.getPostId()).eq(PostThumb::getUserId, event.getUserid());
            boolean updated = postThumbService.update(postThumb,queryWrapper);
            if(!updated){
                boolean save = postThumbService.save(postThumb);
                if(!save){
                    log.warn("执行重试操作");
                    retry(event);
                }
            }
        } catch (BeansException e) {
            retry(event);
        }
    }

    /**
     * 重试机制
     */
    public void retry(ThumbEvent event){
       event.setRetryCount(event.getRetryCount()+1);
       if(event.getRetryCount() >= retry_time){
           sendToDeadLetterQueue(event);
       }else{
           eventQueue.offer(event);
       }
    }

    /**
     * 定期重试机制
     */
    @Scheduled(fixedDelayString = "${rocketmq.consumer.max-reconsume-times}")
    public void retrySchedule(){
        if(eventQueue.isEmpty()) return;
        ConcurrentLinkedQueue<ThumbEvent> queueRetry;
        synchronized (eventQueue){
            queueRetry = new ConcurrentLinkedQueue<>(eventQueue);
            eventQueue.clear();
        }
        queueRetry.forEach(this::processEventGroup);
    }

    /**
     * 发送消息到死信队列
     * @param event
     */
    private void sendToDeadLetterQueue(ThumbEvent event) {
        try {
            rocketMQTemplate.syncSend("DEAD_LETTER_TOPIC",
                    MessageBuilder.withPayload(event)
                            .setHeader(MessageConst.PROPERTY_ORIGIN_MESSAGE_ID, event.getId())
                            .build());
            log.warn("Sent to dead letter queue: {}", event);
        } catch (Exception e) {
            log.error("Failed to send dead letter message", e);
        }
    }
}

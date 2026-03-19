package project.MQ.Consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.MQ.Event.ThumbEvent;
import project.model.entity.PostThumb;
import project.service.PostThumbService;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Component
@Slf4j
@RocketMQMessageListener(
        topic = "THUMB_TOPIC",
        consumerGroup = "thumb-consumer-group",
        consumeThreadMax = 20,
        consumeTimeout = 30000L
)
public class ThumbConsumer implements RocketMQListener<List<ThumbEvent>> {

    private static final ConcurrentLinkedQueue<ThumbEvent> EVENT_QUEUE = new ConcurrentLinkedQueue<>();

    @Resource
    private PostThumbService postThumbService;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.consumer.max-reconsume-times}")
    private int retryTime;

    @Scheduled(fixedDelayString = "${rocketmq.producer.batch-send-delay-ms:10000}")
    public void retrySchedule() {
        if (EVENT_QUEUE.isEmpty()) {
            return;
        }
        ConcurrentLinkedQueue<ThumbEvent> retryQueue = new ConcurrentLinkedQueue<>();
        ThumbEvent event;
        while ((event = EVENT_QUEUE.poll()) != null) {
            retryQueue.offer(event);
        }
        retryQueue.forEach(this::processEvent);
    }

    @Override
    public void onMessage(List<ThumbEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Map<String, ThumbEvent> latestEventMap = events.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        event -> event.getUserid() + "_" + event.getPostId(),
                        event -> event,
                        (left, right) -> compareEventTime(left, right) >= 0 ? left : right
                ));
        latestEventMap.values().forEach(this::processEvent);
    }

    private int compareEventTime(ThumbEvent left, ThumbEvent right) {
        if (left.getDateTime() == null && right.getDateTime() == null) {
            return 0;
        }
        if (left.getDateTime() == null) {
            return -1;
        }
        if (right.getDateTime() == null) {
            return 1;
        }
        return left.getDateTime().compareTo(right.getDateTime());
    }

    private void processEvent(ThumbEvent event) {
        if (event == null) {
            return;
        }
        try {
            PostThumb postThumb = new PostThumb();
            BeanUtils.copyProperties(event, postThumb);
            postThumb.setUserId(event.getUserid());
            postThumb.setIsThumb(event.getIsThumb() == ThumbEvent.ThumbType.THUMB ? 1 : 0);

            LambdaQueryWrapper<PostThumb> queryWrapper = Wrappers.lambdaQuery(PostThumb.class)
                    .eq(PostThumb::getPostId, event.getPostId())
                    .eq(PostThumb::getUserId, event.getUserid());

            PostThumb existing = postThumbService.getOne(queryWrapper);
            boolean success;
            if (ObjectUtils.isEmpty(existing)) {
                success = postThumbService.save(postThumb);
            } else {
                success = postThumbService.update(postThumb, queryWrapper);
            }

            if (!success) {
                retry(event);
                return;
            }
            event.setStatus(1);
        } catch (Exception e) {
            log.warn("thumb consumer failed, event={}", event, e);
            retry(event);
        }
    }

    private void retry(ThumbEvent event) {
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= retryTime) {
            sendToDeadLetterQueue(event);
            return;
        }
        EVENT_QUEUE.offer(event);
    }

    private void sendToDeadLetterQueue(ThumbEvent event) {
        try {
            rocketMQTemplate.syncSend(
                    "DEAD_LETTER_TOPIC",
                    MessageBuilder.withPayload(event)
                            .setHeader(MessageConst.PROPERTY_ORIGIN_MESSAGE_ID, String.valueOf(event.getId()))
                            .build()
            );
            log.warn("sent thumb event to dead letter queue: {}", event);
        } catch (Exception e) {
            log.error("failed to send thumb event to dead letter queue", e);
        }
    }
}

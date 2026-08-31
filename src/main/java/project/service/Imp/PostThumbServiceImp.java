package project.service.Imp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import project.MQ.Event.ThumbEvent;
import project.common.ErrorCode;
import project.exception.ThrowUtil;
import project.mapper.PostThumbMapper;
import project.model.entity.PostThumb;
import project.model.entity.User;
import project.service.PostThumbService;
import project.service.ThumbAsyncPersistenceService;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PostThumbServiceImp extends ServiceImpl<PostThumbMapper, PostThumb> implements PostThumbService {

    private static final String POST_THUMB_KEY = "P:T:";

    private static final String THUMB_SCRIPT =
            "local current = redis.call('GET', KEYS[1])\n" +
                    "if current == false then\n" +
                    "    redis.call('SET', KEYS[1], ARGV[1])\n" +
                    "    return 1\n" +
                    "else\n" +
                    "    redis.call('DEL', KEYS[1])\n" +
                    "    return 0\n" +
                    "end";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ThumbAsyncPersistenceService thumbAsyncPersistenceService;

    @Override
    public int doPostThumb(long postId, User loginUser) {
        ThrowUtil.throwIf(postId <= 0, ErrorCode.PARAMS_ERROR, "postId is invalid");
        ThrowUtil.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "login user is required");

        Long userId = loginUser.getId();
        String redisKey = generateLikeKey(POST_THUMB_KEY + userId + ":" + postId);
        Retryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
                .retryIfException()
                .retryIfResult(result -> result == -1)
                .withWaitStrategy(WaitStrategies.exponentialWait(100, 5, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        try {
            return retryer.call(() -> toggleThumb(userId, postId, redisKey));
        } catch (ExecutionException | RetryException e) {
            log.error("post thumb failed after retries, userId={}, postId={}", userId, postId, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int doPostThumbInner(long userId, long postId) {
        return 1;
    }

    public String generateLikeKey(String original) {
        long hash = Hashing.murmur3_128().hashString(original, StandardCharsets.UTF_8).asLong();
        return "PT:" + Long.toHexString(hash);
    }

    private int toggleThumb(Long userId, long postId, String redisKey) {
        try {
            Long executed = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(THUMB_SCRIPT, Long.class),
                    Collections.singletonList(redisKey),
                    String.valueOf(System.currentTimeMillis())
            );
            if (executed == null) {
                return -1;
            }

            ThumbEvent thumbEvent = new ThumbEvent();
            thumbEvent.setUserid(userId);
            thumbEvent.setPostId(postId);
            thumbEvent.setDateTime(LocalDateTime.now());
            thumbEvent.setIsThumb(executed == 1
                    ? ThumbEvent.ThumbType.THUMB
                    : ThumbEvent.ThumbType.CANCEL_THUMB);
            thumbAsyncPersistenceService.persistAsync(thumbEvent);
            return executed.intValue();
        } catch (Exception e) {
            log.warn("redis thumb toggle unavailable, fallback to mysql, userId={}, postId={}", userId, postId, e);
            return toggleThumbWithoutRedis(userId, postId);
        }
    }

    private int toggleThumbWithoutRedis(Long userId, long postId) {
        LambdaQueryWrapper<PostThumb> queryWrapper = new LambdaQueryWrapper<PostThumb>()
                .eq(PostThumb::getPostId, postId)
                .eq(PostThumb::getUserId, userId);
        PostThumb existing = this.getOne(queryWrapper);
        int newState = (existing == null || existing.getIsThumb() == 0) ? 1 : 0;
        Date now = new Date();

        if (existing == null) {
            PostThumb postThumb = new PostThumb();
            postThumb.setPostId(postId);
            postThumb.setUserId(userId);
            postThumb.setCreateTime(now);
            postThumb.setUpdateTime(now);
            postThumb.setIsThumb(newState);
            this.save(postThumb);
        } else {
            existing.setIsThumb(newState);
            existing.setUpdateTime(now);
            this.updateById(existing);
        }

        ThumbEvent thumbEvent = new ThumbEvent();
        thumbEvent.setUserid(userId);
        thumbEvent.setPostId(postId);
        thumbEvent.setDateTime(LocalDateTime.now());
        thumbEvent.setIsThumb(newState == 1 ? ThumbEvent.ThumbType.THUMB : ThumbEvent.ThumbType.CANCEL_THUMB);
        thumbAsyncPersistenceService.persistAsync(thumbEvent);
        return newState;
    }
}

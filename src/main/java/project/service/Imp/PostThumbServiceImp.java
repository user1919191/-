package project.service.Imp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.rholder.retry.*;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import project.MQ.Event.ThumbEvent;
import project.MQ.Product.ThumbBatchProducer;
import project.common.ErrorCode;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.mapper.PostThumbMapper;
import project.model.entity.PostThumb;
import project.model.entity.User;
import project.service.PostService;
import project.service.PostThumbService;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 帖子点赞业务
 */

@Service
@Slf4j
public class PostThumbServiceImp extends ServiceImpl<PostThumbMapper, PostThumb> implements PostThumbService {

    /**
     * 点赞帖子前缀
     */
    private static final String POST_THUMB_KEY = "P:T:";


  @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ThumbBatchProducer thumbBatchProducer;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ThumbBatchProducer thumbBatchProducer;

    @Resource
    private RedissonClient redissonClient;

    /**
     * Lua脚本点赞
     */
    private static final String THUMB_SCRIPT =
            "local current = redis.call('GET', KEYS[1])\n" +
                    "if current == false then\n" +
                    "    redis.call('SET', KEYS[1], ARGV[1])\n" +
                    "    return 1\n" +
                    "else\n" +
                    "    redis.call('DEL', KEYS[1])\n" +
                    "    return 0\n" +
                    "end";

    /**
     * 点赞帖子
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostThumb(long postId, User loginUser) {
        //1.参数校验
        ThrowUtil.throwIf(postId <=0 , ErrorCode.PARAMS_ERROR, "帖子id错误");
        ThrowUtil.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        //1.1数据生成
        Long userId = loginUser.getId();
        String key = POST_THUMB_KEY + userId + ":" + postId;
        String generateKey = generateLikeKey(key);

        //1.2定义重试策略
        Retryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
                .retryIfException() // 发生异常时重试
                .retryIfResult(result -> result == -1) // 返回-1时重试
                .withWaitStrategy(WaitStrategies.exponentialWait(100, 5, TimeUnit.SECONDS)) // 指数退避
                .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 最多重试3次
                .build();
        //Todo 串行点赞优化
        try {
            return retryer.call(() -> {
//                //2.串行点赞
//                boolean tryLock = true;
//                RLock rLock = redissonClient.getLock(generateKey);
//                try {
//                    tryLock = rLock.tryLock(5, 30, TimeUnit.SECONDS);
//                    if (tryLock) {
                        try {
                            //2.1判断是否点赞(Lua)
                            Long executed = stringRedisTemplate.execute(new DefaultRedisScript<>(THUMB_SCRIPT, Long.class)
                                    , Collections.singletonList(generateKey), String.valueOf(System.currentTimeMillis()));
                            //2.2生成实体类
                            ThumbEvent thumbEvent = new ThumbEvent();
                            thumbEvent.setUserid(userId);
                            thumbEvent.setPostId(postId);
                            thumbEvent.setDateTime(LocalDateTime.now());
                            thumbEvent.setIsThumb(executed == 1 ? ThumbEvent.ThumbType.THUMB : ThumbEvent.ThumbType.CANCEL_THUMB);

                            //2.3添加到消息队列
                            thumbBatchProducer.AddThumb(thumbEvent);
                            return executed.intValue();

                        } catch (MessagingException e) {
                            return -1;
                        }
//                    }
//                } catch (InterruptedException e) {
//                    return -1;
//                } finally {
//                    if (tryLock && rLock.isHeldByCurrentThread()) {
//                        rLock.unlock();
//                    }
//                }
//                return -1;
            });
        } catch (BusinessException | ExecutionException | RetryException e) {
            throw new RuntimeException(e);
        }
        //点赞失败返回
    }

    //Todo 是否可以优化
    @Override
    public int doPostThumbInner(long userId, long postId) {
//        PostThumb postThumb = new PostThumb();
//        postThumb.setUserId(userId);
//        postThumb.setPostId(postId);
//
//        boolean result;
//        QueryWrapper<PostThumb> queryWrapper = new QueryWrapper<>(postThumb);
//        PostThumb oldThumbStatus = this.getOne(queryWrapper);
//        if(oldThumbStatus ==  null){
//            result = this.save(postThumb);
//            if (result) {
//                result =postService.update().eq("id",postId).setSql("thumbNum = thumbNum + 1").update();
//                return result ? -1:0;
//            }else{
//                throw new BusinessException(ErrorCode.OPERATION_ERROR);
//            }
//        }else{
//            result = this.remove(queryWrapper);
//            if(result){
//                result = postService.update().eq("id",postId).gt("thumbNum",0).setSql("thumbNum = thumbNum - 1").update();
//                return result ? 1:0;
//            }else{
//                throw new BusinessException(ErrorCode.OPERATION_ERROR);
//            }
//        }
        //取消注释删除
        return 1;
    }

    /**
     * 使用压缩算法MurmurHash3,生成压缩Key
     * @param original
     * @return 压缩后的Key
     */
    //Todo 概率极低，MurmurHash3冲突率约1/2^64
    public String generateLikeKey(String original) {
        long hash = Hashing.murmur3_128().hashString(original, StandardCharsets.UTF_8).asLong();
        // 转为16进制缩短长度
        return "PT:" + Long.toHexString(hash);
    }
}

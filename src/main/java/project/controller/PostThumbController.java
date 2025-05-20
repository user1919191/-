package project.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.IdUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.math3.analysis.function.Min;
import org.apache.poi.ss.formula.functions.Rate;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.MQ.Product.ThumbBatchProducer;
import project.annotation.RateLimiter;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.ThrowUtil;
import project.model.dto.postthumb.PostThumbAddRequest;
import project.model.entity.RetryThumb;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.service.Imp.PostThumbServiceImp;
import project.service.PostService;
import project.service.PostThumbService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 帖子点赞接口
 */

@RestController
@RequestMapping("/post_thumb")
@Slf4j
public class PostThumbController {

    /**
     * 线程池参数
     */
    final int CORE_POOL_SIZE = 20;
    final int MAX_POOL_SIZE = 50;
    final int QUEUE_CAPACITY = 10000;

    /**
     * 点赞Key前缀
     */
    private static final String POST_THUMB_KEY = "pt:tb:";

    /**
     * 重试次数
     */
    private Long RETRY_COUNT = 3L;

    /**
     * 类线程池
     */
    ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * 延迟队列
     */
    private LinkedBlockingQueue<RetryThumb> delayedQueue = null;

    //降级策略存储Redis
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private PostThumbService postThumbService;

    @Resource
    private UserService userService;

    @Resource
    private PostService postService;

    /**
     * 点赞/取消点赞
     * @param thumbRequest
     * @param request
     * @return
     */
    @PostMapping("/")
    @RateLimiter(key = "doThumb",CountTime = 1,LimitCount = 5,timeUnit = RateIntervalUnit.SECONDS,limitType = LimitTypeEnum.REJECT_USER)
    @SentinelResource( value = "doThumb",blockHandler = "blockHandler",fallback = "fallback")
    public BaseResponse<Integer> doThumb(@RequestBody PostThumbAddRequest thumbRequest, HttpServletRequest request){
        //1.参数校验
        Long postId = thumbRequest.getPostId();
        ThrowUtil.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR, "帖子id错误");
        //2.登录校验
        final User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NOT_LOGIN, "用户未登录");
        //3.业务处理
        int doPostThumb = postThumbService.doPostThumb(postId, loginUser);
        boolean increased = false;
        if(doPostThumb == 1){
            increased = postService.getById(postId).IncreaseThumb();
        }else{
            if(doPostThumb == -1){
                increased = postService.getById(postId).DecreaseThumb();
            }
        }
        return ResultUtil.success( increased ? doPostThumb : 0 );
    }

    /**
     * 本地限流策略
     * @param thumbRequest
     * @param request
     * @param ex
     * @return
     */
    public BaseResponse<Integer> blockHandler(@RequestBody PostThumbAddRequest thumbRequest, HttpServletRequest request, BlockException ex){
        return ResultUtil.error(ErrorCode.SYSTEM_ERROR,"系统压力过大,请等待");
    }

    /**
     * 降级策略
     * @param thumbRequest
     * @param request
     * @param ex
     * @return
     */
    public BaseResponse<Integer> fallback(@RequestBody PostThumbAddRequest thumbRequest, HttpServletRequest request, Throwable ex){
        //1.获取参数
        Long postId = thumbRequest.getPostId();
        User loginUser = userService.getLoginUser(request);
        //2.登录校验
        ThrowUtil.throwIf(loginUser == null, ErrorCode.NOT_LOGIN, "用户未登录");
        Long loginUserId = loginUser.getId();
        //3.生成RedisKey
        RetryThumb retryThumb = new RetryThumb(UUID.randomUUID().toString(), postId,loginUser);
        String key = POST_THUMB_KEY + postId + ":" + loginUserId;
        RLock redissonClientLock = redissonClient.getLock(key);
        try{
            if (redissonClientLock.tryLock(1,5, TimeUnit.SECONDS)) {
                //4.幂等判断
                if (redissonClient.getBucket(key).isExists()) {
                    return ResultUtil.success(0);
                }
                //添加键值对到Redis
                redissonClient.getBucket(key).set(1);
                //5.添加到本地延迟队列
                boolean added = delayedQueue.add(retryThumb);
                ThrowUtil.throwIf(!added, ErrorCode.OPERATION_ERROR, "点赞失败");
            }
        } catch (InterruptedException e) {
            ThrowUtil.throwIf(true, ErrorCode.OPERATION_ERROR, "降级策略中点赞失败");
        }finally {
            redissonClientLock.unlock();
        }
        return ResultUtil.success(1);
    }

    /**
     * 错峰定时任务批量执行点赞
     */
    public void RDelayBatchAdd(List<RetryThumb> retryThumbs,int count) {
        // 1. 参数准备
        final int BATCH_SIZE = 100;
        final long MAX_RETRY = RETRY_COUNT;

        try {
            // 2. 批量处理队列中的点赞任务
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            while (!delayedQueue.isEmpty()) {
                // 2.1批量获取任务
                List<RetryThumb> batch = new ArrayList<>(BATCH_SIZE);
                delayedQueue.drainTo(batch, BATCH_SIZE);
                if( batch.size() == 0){
                    log.warn("没有获取到点赞任务");
                }
                // 2.2创建异步任务
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    PostThumbServiceImp thumbService = (PostThumbServiceImp) AopContext.currentProxy();
                    //2.3 带重试机制的批量处理
                    for (int retry = 0; retry <= MAX_RETRY; retry++) {
                        try {
                            for (RetryThumb thumb : batch) {
                                thumbService.doPostThumb(thumb.getPostId(), thumb.getUser());
                            }
                            break;
                        } catch (Exception e) {
                            if (retry == MAX_RETRY) {
                                log.error("Failed to process thumb batch after {} retries", MAX_RETRY, e);
                            } else {
                                log.warn("Retry {} for thumb batch failed, will retry", retry, e);
                                try {
                                    //延时重试
                                    Thread.sleep(1000 * (retry + 1));
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                }, threadPool);
                futures.add(future);
            }

            //3.等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            //4.关闭线程池
            threadPool.shutdown();
        }
    }
}

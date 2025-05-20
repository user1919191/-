package project.aop;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.annotation.RateLimiter;
import project.balckFilter.BlackIpUtils;
import project.common.ErrorCode;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.model.enums.RateIntercvalEnum;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 防止爬虫AOP切面
 */
@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    /**
     * 封禁档次
     */
    private static long temporaryBanTime = 30 * 60 * 1000L;

    /**
     * Redis根据IP前缀
     */
    private static final String LimiterIpPrefix = "limit:ip:";
    private static final String LimiterIpCountPrefix = "IP:count:";

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private HttpServletRequest request;

//    // 使用ConcurrentHashMap缓存限流器
//    private final ConcurrentHashMap<String, RRateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    /**
     * 执行限流方法
     * @param joinPoint
     * @param rateLimiter
     * @return
     * @throws Throwable
     */
    //Todo 没有持久化
    @Around("@annotation(rateLimiter)")
    public Object rateLimiter(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter) throws Throwable {
        String limitType = rateLimiter.limitType().getLimitType();
        //1.获取封禁类型
        String key = null;
        if(!limitType.equals(LimitTypeEnum.REJECT_IP.getLimitTypeCode())){
            key = LimiterIpPrefix + getClientIp();
        }
        ThrowUtil.throwIf(StringUtils.isEmpty(key), ErrorCode.OPERATION_ERROR,"IP地址异常,请咨询管理员");
        TimeUnit timeUnit = RateIntercvalEnum.toTimeUnit(rateLimiter.timeUnit());
        //2.获分布式锁(可重入)
        boolean tryLock = false;
        RLock rLock = redissonClient.getLock(key);
        RAtomicLong atomicLong = redissonClient.getAtomicLong(LimiterIpCountPrefix+key);
        try {
            tryLock = rLock.tryLock(1, 5, TimeUnit.SECONDS);
            if(tryLock){
                //3.获取当前访问次数
                long currentCount = atomicLong.getAndIncrement();
                if(currentCount == 0){
                    atomicLong.expire(rateLimiter.CountTime(), timeUnit);
                }
                //4.触发限流,封禁账号
                if(currentCount >= rateLimiter.LimitCount()){
                    User loginUser = userService.getLoginUser(request);
                    if(ObjectUtils.isEmpty(loginUser)){
                        //未登录直接封禁IP
                        BlackIpUtils.addBlackIp(getClientIp());
                    }else{
                        //将用户设置为账号封禁
                        loginUser.setUserRole("ban");
                        userService.updateById(loginUser);
                        //将用户踢下线
                        userService.userLogOut(request);
                    }
                    log.warn("反爬虫触发 -,key:{},IP:{},user:{}", key,getClientIp(),getUserId());
                    throw new BusinessException(ErrorCode.NOT_VISIT);
                }
            }else{
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"系统繁忙请稍后重试");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e){
            throw new Exception(e);
        }finally {
            //5.Todo 踩坑笔记:释放锁(必须判断是否持有锁并当前线程持有锁,否则会抛出IllegalMonitorStateException异常)
            if ( tryLock && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
        //Todo 是否可以优化,将封禁信息提交给管理员审核,如果封禁添加到Nacos加入BloomFilter
        return joinPoint.proceed();
    }

    /**
     * 生成Key
     * @param proceedingJoinPoint
     * @param rateLimiter
     * @return
     */
//    private String getLVisitKey(ProceedingJoinPoint proceedingJoinPoint, RateLimiter rateLimiter) {
//        //1.获取方法签名
//        MethodSignature signature = (MethodSignature)proceedingJoinPoint.getSignature();
//        //2.获取类名方法名
//        String simpleClassName = signature.getDeclaringType().getSimpleName();
//        String methodName = signature.getName();
//        StringBuilder primaryKey = new StringBuilder("RL:" + simpleClassName+ "." + methodName);
//        switch (rateLimiter.limitType()) {
//            case REJECT_IP:
//                primaryKey.append(":ip:").append(getClientIp());
//                break;
//            case REJECT_USER:
//                primaryKey.append(":user:").append(getUserId());
//                break;
//            case REJECT_ALL:  // IP+用户双重维度
//                primaryKey.append(":ip_user:")
//                        .append(getClientIp())
//                        .append("_")
//                        .append(getUserId());
//                break;
//            default:
//                break;
//        }
//        return primaryKey.toString();
//    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        return StringUtils.isNotEmpty(ip) ? ip.split(",")[0] : "unknown_ip";
    }

    /**
     * 获取用户ID
     */
    private Long getUserId() {
        try {
            // 从token或session中获取用户ID
            String token = request.getHeader("Authorization");
            HttpSession session = request.getSession(false);
            if (session != null) {
                return (Long) session.getAttribute("userId");
            }
        } catch (Exception e) {
            log.error("获取用户ID失败", e);
        }
        return 0L;
    }

//    /**
//     * 初始化限流器
//     * @param key
//     * @param rateLimiter
//     * @return
//     */
//    private RRateLimiter initRateLimiter(String key, RateLimiter rateLimiter) {
//        RRateLimiter limiter = redissonClient.getRateLimiter(key);
//        limiter.trySetRate(RateType.OVERALL,rateLimiter.LimitCount(), rateLimiter.CountTime(), rateLimiter.timeUnit());
//        return limiter;
//    }
}

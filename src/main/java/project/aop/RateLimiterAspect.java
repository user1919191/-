package project.aop;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import project.annotation.RateLimiter;
import project.common.ErrorCode;
import project.exception.BusinessException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 防止爬虫AOP切面
 */
@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    // 使用ConcurrentHashMap缓存限流器
    private final ConcurrentHashMap<String, RRateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private HttpServletRequest request;

    /**
     * 执行限流方法
     * @param joinPoint
     * @param rateLimiter
     * @return
     * @throws Throwable
     */
    @Around("@annotation(rateLimiter)")
    public Object rateLimiter(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter) throws Throwable {
        String key = getLVisitKey(joinPoint, rateLimiter);
        RRateLimiter limiter = rateLimiterCache.get(key);
        if (limiter == null) {
            limiter = initRateLimiter(key, rateLimiter);
            RRateLimiter existing = rateLimiterCache.putIfAbsent(key, limiter);
            if (existing != null) {
                limiter = existing;
            }
        }

        if (!limiter.tryAcquire()) {
            log.warn("限流触发 -,key:{},IP:{},user:{}", key,getClientIp(),getUserId());
            throw new BusinessException(ErrorCode.NOT_VISIT);
        }

        return joinPoint.proceed();
    }

    /**
     * 生成Key
     * @param proceedingJoinPoint
     * @param rateLimiter
     * @return
     */
    private String getLVisitKey(ProceedingJoinPoint proceedingJoinPoint, RateLimiter rateLimiter) {
        //1.获取方法签名
        MethodSignature signature = (MethodSignature)proceedingJoinPoint.getSignature();
        //2.获取类名方法名
        String simpleClassName = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        StringBuilder primaryKey = new StringBuilder("RL:" + simpleClassName+ "." + methodName);
        switch (rateLimiter.limitType()) {
            case REJECT_IP:
                primaryKey.append(":ip:").append(getClientIp());
                break;
            case REJECT_USER:
                primaryKey.append(":user:").append(getUserId());
                break;
            case REJECT_ALL:  // IP+用户双重维度
                primaryKey.append(":ip_user:")
                        .append(getClientIp())
                        .append("_")
                        .append(getUserId());
                break;
            default:
                break;
        }
        return primaryKey.toString();
    }

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

    /**
     * 初始化限流器
     * @param key
     * @param rateLimiter
     * @return
     */
    private RRateLimiter initRateLimiter(String key, RateLimiter rateLimiter) {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL,rateLimiter.LimitCount(), rateLimiter.CountTime(), rateLimiter.timeUnit());
        return limiter;
    }
}

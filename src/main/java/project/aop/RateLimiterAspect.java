package project.aop;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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
import java.util.concurrent.atomic.AtomicLong;

@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    private static final String LIMITER_IP_PREFIX = "limit:ip:";
    private static final String LIMITER_IP_COUNT_PREFIX = "IP:count:";

    private final ConcurrentHashMap<String, LocalRateLimitWindow> localRateLimitWindows = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private HttpServletRequest request;

    @Around("@annotation(rateLimiter)")
    public Object rateLimiter(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter) throws Throwable {
        String limitType = rateLimiter.limitType().getLimitType();
        String key = null;
        if (!limitType.equals(LimitTypeEnum.REJECT_IP.getLimitTypeCode())) {
            key = LIMITER_IP_PREFIX + getClientIp();
        }
        ThrowUtil.throwIf(StringUtils.isEmpty(key), ErrorCode.OPERATION_ERROR, "IP address is invalid");
        TimeUnit timeUnit = RateIntercvalEnum.toTimeUnit(rateLimiter.timeUnit());
        try {
            checkDistributedLimit(key, rateLimiter, timeUnit);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            checkLocalLimit(key, rateLimiter, timeUnit, e);
        }
        return joinPoint.proceed();
    }

    private void checkDistributedLimit(String key, RateLimiter rateLimiter, TimeUnit timeUnit) throws Exception {
        boolean tryLock = false;
        RLock rLock = null;
        try {
            rLock = redissonClient.getLock(key);
            RAtomicLong atomicLong = redissonClient.getAtomicLong(LIMITER_IP_COUNT_PREFIX + key);
            tryLock = rLock.tryLock(1, 5, TimeUnit.SECONDS);
            if (!tryLock) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "System is busy");
            }
            long currentCount = atomicLong.getAndIncrement();
            if (currentCount == 0) {
                atomicLong.expire(rateLimiter.CountTime(), timeUnit);
            }
            if (currentCount >= rateLimiter.LimitCount()) {
                handleExceededLimit(key);
            }
        } finally {
            if (tryLock && rLock != null && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    private void checkLocalLimit(String key, RateLimiter rateLimiter, TimeUnit timeUnit, Exception cause) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1L, timeUnit.toMillis(rateLimiter.CountTime()));
        LocalRateLimitWindow window = localRateLimitWindows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStart.get() >= windowMillis) {
                return new LocalRateLimitWindow(now);
            }
            return existing;
        });
        long currentCount = window.counter.getAndIncrement();
        if (currentCount >= rateLimiter.LimitCount()) {
            log.warn("Redisson unavailable, local rate limiter triggered, key={}", key, cause);
            throw new BusinessException(ErrorCode.NOT_VISIT);
        }
    }

    private void handleExceededLimit(String key) {
        User loginUser = null;
        try {
            loginUser = userService.getLoginUser(request);
        } catch (Exception ignored) {
        }
        if (ObjectUtils.isEmpty(loginUser)) {
            BlackIpUtils.addBlackIp(getClientIp());
        } else {
            loginUser.setUserRole("ban");
            userService.updateById(loginUser);
            userService.userLogOut(request);
        }
        log.warn("Rate limit exceeded, key={}, ip={}, user={}", key, getClientIp(), getUserId());
        throw new BusinessException(ErrorCode.NOT_VISIT);
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return StringUtils.isNotEmpty(ip) ? ip.split(",")[0] : "unknown_ip";
    }

    private Long getUserId() {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                return (Long) session.getAttribute("userId");
            }
        } catch (Exception e) {
            log.error("Failed to read user id", e);
        }
        return 0L;
    }

    private static final class LocalRateLimitWindow {
        private final AtomicLong counter = new AtomicLong(0);
        private final AtomicLong windowStart;

        private LocalRateLimitWindow(long startTime) {
            this.windowStart = new AtomicLong(startTime);
        }
    }
}

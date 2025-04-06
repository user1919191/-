package project.annotation;

import org.redisson.api.RateIntervalUnit;
import project.model.enums.LimitTypeEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimiter {
    /**
     * 限流key
     */
    String key() default "";

    /**
     * 限流时间窗口，单位s
     */
    long CountTime() default 10;

    /**
     * 时间窗口内允许的请求次数
     */
    long LimitCount() default 10;

    /**
     * 时间单位
     */
    RateIntervalUnit timeUnit() default RateIntervalUnit.SECONDS;
    /**
     * 限流类型 (自定义枚举，支持IP、用户等维度)
     */
    LimitTypeEnum limitType() default LimitTypeEnum.REJECT_USER;
}

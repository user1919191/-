package project.model.enums;

import org.apache.poi.ss.formula.functions.Rate;
import org.redisson.api.RateIntervalUnit;

import java.util.concurrent.TimeUnit;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 限流时间转化枚举
 */

public enum RateIntercvalEnum {
    NANOSECONDS,
    MICROSECONDS,
    MILLISECONDS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS;

    public static TimeUnit toTimeUnit(RateIntervalUnit rateIntervalUnit) {
        switch (rateIntervalUnit) {
            case MILLISECONDS:
                return TimeUnit.MILLISECONDS;
            case SECONDS:
                return TimeUnit.SECONDS;
            case MINUTES:
                return TimeUnit.MINUTES;
            case HOURS:
                return TimeUnit.HOURS;
            case DAYS:
                return TimeUnit.DAYS;
            default:
                throw new IllegalArgumentException("Unknown RateIntervalUnit: " + rateIntervalUnit);
        }
    }
}

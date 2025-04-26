package project.model.enums;

import org.apache.poi.ss.formula.functions.Rate;
import org.redisson.api.RateIntervalUnit;

import java.util.concurrent.TimeUnit;

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

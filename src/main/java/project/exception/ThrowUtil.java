package project.exception;
import project.common.ErrorCode;

/**
 * 抛异常工具类
 */

public class ThrowUtil {
    /**
     * 条件成立立即抛异常
     * @param condition 条件
     * @Param runtimeExceptionClass 异常类型
     */
    public static void throwIf(boolean condition,RuntimeException runtimeExceptionClass){
        if(condition){
            throw new RuntimeException();
        }
    }

    /**
     * 条件成立立即抛异常
     * @param condition 条件
     * @Param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode){
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立立即抛异常
     * @Param condition 条件
     * @Param errorCode 错误码
     * @Param msg 错误信息
     */
    public static void throwIf(boolean condition, ErrorCode errorCode,String msg){
        //Todo msg返回不到前端,跟踪BusinessException优化
        throwIf(condition,new BusinessException(errorCode,msg));
    }
}

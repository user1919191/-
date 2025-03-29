package project.common;

/**
 * 返回工具类
 */

public class ResultUtil {

    /**
     * 成功返回结果
     * @Param data 获取的数据
     */
    public static <T> BaseResponse<T> success(T data){
        return new BaseResponse<>(ErrorCode.SUCCESS.getCode(),data,ErrorCode.SUCCESS.getMeg());
    }

    /**
     * 失败返回结果
     * @Param errorCode 错误码
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode.getCode(),null,errorCode.getMeg());
    }

    /**
     * 失败返回结果
     * @Param code 失败码
     * @Param msg 失败信息
     */
    public static <T> BaseResponse<T> error(int code,String msg){
        return new BaseResponse<>(code,null,msg);
    }

    /**
     * 失败返回结果
     * @Param errorCode 错误码
     * @Param msg 失败信息
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode,String msg){
        return new BaseResponse<>(errorCode.getCode(),null,msg);
    }

}

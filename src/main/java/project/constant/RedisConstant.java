package project.constant;

/**
 * Redis常量
 */
public interface RedisConstant {
    String Prefix_User_In_Redis_Key = "user:singins";

    /**
     * 获取用户签到记录的key
     * @Param 年份
     * @Param 用户id账号
     */

    static String getUserSignInKey(int year, String userId) {
        return String.format("%s:%s:%s",Prefix_User_In_Redis_Key, year, userId);
    }
}

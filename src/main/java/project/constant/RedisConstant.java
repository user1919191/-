package project.constant;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * Redis常量
 */
public interface RedisConstant {

    String Prefix_User_In_Redis_Key = "user:singins";

    /**
     * 存储年份最小值
     */
    Integer MinYear = 2023;

    /**
     * 获取用户签到记录的key
     * @Param 年份
     * @Param 用户id账号
     */
    static String getUserSignInKey(int year,long userId) {
        return String.format("%s:%s:%s",Prefix_User_In_Redis_Key, year, userId);
    }
}

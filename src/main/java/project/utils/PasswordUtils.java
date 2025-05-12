package project.utils;

import org.mindrot.jbcrypt.BCrypt;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 密码加密工具类
 */

public class PasswordUtils {

    /**
     * 生成加密密码
     * @param password
     * @return 加密后的密码
     */
    public static String hashPassword(String password) {
        //使用BCrypt自动生成盐
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * 校验密码
     * @param password
     * @param hashedPassword
     * @return 密码是否正确
     */
    public static boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }
}

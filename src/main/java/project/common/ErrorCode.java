package project.common;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 自定义错误码
 */

public enum ErrorCode {
    SUCCESS(200,"成功"),
    PARAMS_ERROR(400,"参数错误"),
    NOT_LOGIN(401,"未注册"),
    NOT_REGION(402,"未登录"),
    NOT_ROLE(403,"权限不足"),
    NOT_VIP(404,"需要VIP"),
    NOT_FOUND(405,"未找到"),
    NOT_VISIT(406,"禁止访问"),
    UNKNOW_ERROR(500,"未知错误"),
    OPERATION_ERROR(501,"操作失败"),
    SYSTEM_ERROR(502,"系统错误");

    /**
     * 状态码
     */
    private int code;
    /**
     * 状态信息
     */
    private String meg;

    ErrorCode(int code, String meg) {
        this.code = code;
        this.meg = meg;
    }

    public int getCode() {
        return code;
    }

    public String getMeg() {
        return meg;
    }
}

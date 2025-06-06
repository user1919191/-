package project.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 全局异常处理拦截器
 */

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusinessException(BusinessException e) {
        log.error("BusinessException：{}", e.getMessage());
        return ResultUtil.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtil.error(ErrorCode.SYSTEM_ERROR, "运行错误");
    }

    @ExceptionHandler(NotRoleException.class)
    public BaseResponse<?> notRoleExceptionHandler(RuntimeException e) {
        log.error("NotRoleException", e);
        return ResultUtil.error(ErrorCode.NOT_ROLE, "无权限");
    }

    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginExceptionHandler(RuntimeException e) {
        log.error("NotLoginException", e);
        return ResultUtil.error(ErrorCode.NOT_LOGIN, "未登录");
    }
}

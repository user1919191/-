package project.aop;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import project.annotation.AutoCheck;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import project.common.ErrorCode;
import project.constant.UserConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.model.entity.User;
import project.model.enums.UserRoleEnum;
import project.service.UserService;
import springfox.documentation.RequestHandler;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 校验权限拦截类
 */

@Aspect
@Component
public class AutoCheckInterceptor {
    @Resource
    private UserService userService;

    @Around("@annotation(autoCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint,AutoCheck autoCheck) throws Throwable {
        String mustRole = autoCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 当前登录用户
        User loginUser = userService.getLoginUser(httpServletRequest);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 必须有该权限才通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NOT_ROLE);
        }
        // 如果被封号，直接拒绝
        if (UserRoleEnum.BAN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NOT_VISIT);
        }
        // 必须有管理员权限
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum)) {
            // 用户没有管理员权限，拒绝
            if (!UserRoleEnum.ADMIN.equals(userRoleEnum)) {
                throw new BusinessException(ErrorCode.NOT_ROLE);
            }
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}

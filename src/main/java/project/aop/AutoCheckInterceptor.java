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
    public Object around(ProceedingJoinPoint joinPoint,AutoCheck autoCheck) throws Throwable {
        String mustRole = autoCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();

        //获取当前登录用户
        User loginUser = userService.getLoginUser(httpServletRequest);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);

        //获取登录用户的权限
        UserRoleEnum loginUserRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());

        if(mustRole == null){
            return joinPoint.proceed();
        }

        if(loginUserRoleEnum == null){
            throw new BusinessException(ErrorCode.NOT_ROLE);
        }

        if(loginUserRoleEnum.equals(UserRoleEnum.BAN)){
            throw new BusinessException(ErrorCode.NOT_ROLE);
        }

        if(mustRoleEnum.equals(UserRoleEnum.ADMIN)){
            if(!loginUserRoleEnum.equals(UserRoleEnum.ADMIN)){
                throw new BusinessException(ErrorCode.NOT_ROLE);
            }
        }
        return joinPoint.proceed();
    }
}

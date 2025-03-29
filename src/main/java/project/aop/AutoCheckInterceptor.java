package project.aop;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import project.annotation.AutoCheck;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import project.exception.ThrowUtil;
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
        User user = userService.getUser(httpServletRequest);
        if(user == null){
            ThrowUtil
        }
    }
}

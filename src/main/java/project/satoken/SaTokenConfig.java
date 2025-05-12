package project.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * SaToken 配置类
 */

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    /**
     * 配置拦截器打开拦截功能
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //打开注解式鉴权
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}

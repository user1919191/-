package project.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

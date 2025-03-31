package project.balckFilter;

import project.utils.NetUtil;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 黑名单全局Ip过滤拦截器
 */
@WebFilter(urlPatterns = "/*",filterName = "BlackIpFilter")
public class BlackIpFilter implements Filter{

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String ipAddress = NetUtil.getIpAddress((HttpServletRequest) servletRequest);
        if(BlackIpUtils.isBlack(ipAddress)){
            servletResponse.setContentType("text/json;charset=UTF-8");
            servletResponse.getWriter().write("{\"errorCode\":\"-1\",\"errorMsg\":\"黑名单IP，禁止访问\"}");
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}

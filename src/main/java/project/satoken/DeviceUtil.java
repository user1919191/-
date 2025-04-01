package project.satoken;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import project.exception.ThrowUtil;

import javax.servlet.http.HttpServletRequest;
import project.common.ErrorCode;

/**
 * 设备工具类
 */

public class DeviceUtil {


    /**
     * 根据请求获取当前设备类型
     */
    public static String getDeviceType(HttpServletRequest request) {
        String header = request.getHeader(Header.USER_AGENT.toString());
        UserAgent parse = UserAgentUtil.parse(header);
        ThrowUtil.throwIf(parse == null, ErrorCode.NOT_LOGIN);

        String defaultDevice = "PC";
        if (isMiniProgram(header)) {
            defaultDevice = "MiniProgram";
        }else if (isPad(header)) {
            defaultDevice = "Pad";
        } else if (parse.isMobile()) {
            defaultDevice = "Mobile";
        }
        return defaultDevice;
    }

    /**
     * 当前设备是否为小程序登录
     */
    public static boolean isMiniProgram(String header) {
        return StrUtil.containsIgnoreCase(header, "MicroMessenger")
                && StrUtil.containsIgnoreCase(header, "MiniProgram");
    }

    //是否为平板登录
    public static boolean isPad(String header) {
        boolean ispad = StrUtil.containsIgnoreCase(header, "iPad");

        boolean isAndroidTablet = StrUtil.containsIgnoreCase(header, "Android")
                && !StrUtil.containsIgnoreCase(header, "Mobile");

        return ispad || isAndroidTablet;
    }
}

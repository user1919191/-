package project.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import project.model.entity.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import project.constant.UserConstant;

/**
 * 自定义泉下加载实现类
 */

@Component
public class StpInterfaceImp implements StpInterface {

    @Override
    public List<String> getPermissionList(Object o, String s) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object LongId, String LoginType) {
        User user = (User)StpUtil.getSessionBySessionId(LongId.toString()).get(UserConstant.User_Status_Login);
        return Collections.singletonList(user.getUserRole());
    }
}

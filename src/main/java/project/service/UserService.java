package project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import project.model.dto.user.UserQueryRequest;
import project.model.entity.User;
import project.model.vo.UserVO;
import project.model.vo.loginUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务接口
 */

public interface UserService extends IService<User> {
    /**
     * 用户账号注册
     * @param userAccount
     * @param userPassword
     * @param checkPassword
     * @return 用户ID
     */
    Long UserRegister(String userAccount,String userPassword,String checkPassword);

    /**
     * 用户登录
     * @param userAccount
     * @param userPassword
     * @return 用户信息
     */
     loginUserVO login(String userAccount, String userPassword, HttpServletRequest httpServletRequest);

    /**
     *获取当前登录用户
     * @param httpServletRequest
     * @return
     */
    User getLoginUser(HttpServletRequest httpServletRequest);

    /**
     *获取当前登录用户(允许未登录)
     * @param httpServletRequest
     * @return
     */
     User getLoginUserPermitNull(HttpServletRequest httpServletRequest);

    /**
     * 是否为管理员
     * @param httpServletRequest
     * @return
     */
     boolean isAdmin(HttpServletRequest httpServletRequest);

    /**
     * 用户注销
     * @param httpServletRequest
     * @return
     */
     boolean userLogOut(HttpServletRequest httpServletRequest);

    /**
     * 获取当前登录用户信息(脱敏)
     * @param user
     * @return
     */
     loginUserVO getLoginUserVO(User user);

    /**
     * 获取用户信息(脱敏)
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取用户信息(脱敏)
     * @param userList
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取user的查询条件
     * @param userqueryrequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userqueryrequest);

    /**
     * 添加用户签到记录
     * @param userId
     * @return 是否签到成功
     */
    boolean addUserSignIn(long userId);

    /**
     * 获取用户某年的签到记录
     * @param userId
     * @return
     */
    List<Integer> getUserSignInYear(long userId, Integer year);

}

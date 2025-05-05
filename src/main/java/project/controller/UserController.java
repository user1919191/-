package project.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.StrUtil;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import project.annotation.RateLimiter;
import project.common.BaseResponse;
import project.common.DeleteRequest;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.model.dto.user.*;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.model.vo.UserVO;
import project.model.vo.loginUserVO;
import project.service.UserService;
import project.utils.PasswordUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 用户接口
 */

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        //1.参数校验
        ThrowUtil.throwIf(userRegisterRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        ThrowUtil.throwIf(StringUtils.isAnyBlank(userAccount,userPassword,checkPassword)
                ,new BusinessException(ErrorCode.PARAMS_ERROR,"请输入账号或者密码或者确认密码"));
        //校验两次密码是否相同
        ThrowUtil.throwIf(!userPassword.equals(checkPassword),ErrorCode.PARAMS_ERROR,"两次密码不一致");
        long result = userService.UserRegister(userAccount,userPassword,checkPassword);
        return ResultUtil.success(result);
    }

    /**
     * 用户登录
     * @param userLoginRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<loginUserVO> login(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest httpServletRequest) {
        //1.参数校验
        ThrowUtil.throwIf(userLoginRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        //2.登录
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        //3.格式校验
        if(userAccount.length() < 4 || userAccount.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号格式错误");
        }
        if (userPassword.length() < 8 || userPassword.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码格式错误");
        }
        loginUserVO loginUserVO = userService.login(userAccount, userPassword, httpServletRequest);
        //4.返回结果
        return ResultUtil.success(loginUserVO);
    }

    /**
     * 用户注销
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest httpServletRequest) {
        ThrowUtil.throwIf(httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        boolean isSuccess = userService.userLogOut(httpServletRequest);
        return ResultUtil.success(isSuccess);
    }

    /**
     * 获取当前登录用户信息(脱敏)
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/get/login")
    @SaCheckRole()
    public BaseResponse<loginUserVO> getLoginUserVO(HttpServletRequest httpServletRequest) {
        ThrowUtil.throwIf(httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtil.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 新增用户(管理员功能)
     * @param userAddRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest,HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(userAddRequest == null ||httpServletRequest == null ,ErrorCode.PARAMS_ERROR,"传入参数为空");

        //2.用户参数校验
        if(userAddRequest.getUserAccount().length() < 4 || userAddRequest.getUserAccount().length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号格式错误");
        }
        if(!StrUtil.containsAnyIgnoreCase(userAddRequest.getUserRole(),"Admin","User")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户角色错误");
        }

        //3.新增用户
        User user = new User();
        BeanUtils.copyProperties(userAddRequest,user);
        String defaultPassword = "12345678";
        String hashPassword = PasswordUtils.hashPassword(defaultPassword);
        user.setUserPassword(hashPassword);
        boolean save = userService.save(user);
        ThrowUtil.throwIf(!save,new BusinessException(ErrorCode.SYSTEM_ERROR,"添加用户失败"));
        return ResultUtil.success(user.getId());
    }

    /**
     * 删除用户(管理员功能)
     * @param userDeleteRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest userDeleteRequest, HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(userDeleteRequest == null ||httpServletRequest == null ,ErrorCode.PARAMS_ERROR,"传入参数为空");
        ThrowUtil.throwIf(userDeleteRequest.getId() <= 0 ,new BusinessException(ErrorCode.PARAMS_ERROR,"传入用户ID不合法"));

        //2.删除用户
        boolean IsDelete = userService.removeById(userDeleteRequest.getId());
        ThrowUtil.throwIf(!IsDelete,new BusinessException(ErrorCode.SYSTEM_ERROR,"删除用户失败"));
        return ResultUtil.success(IsDelete);
    }

    /**
     * 更新用户(管理员功能)
     * @param userUpdateRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(userUpdateRequest == null || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        ThrowUtil.throwIf(userUpdateRequest.getId() <= 0 ,new BusinessException(ErrorCode.PARAMS_ERROR,"传入用户ID不合法"));

        //2.检查用户是否存在
        User user = userService.getById(userUpdateRequest.getId());
        ThrowUtil.throwIf(user == null,new BusinessException(ErrorCode.NOT_FOUND,"用户不存在"));

        //3.更新敏感内容记录
        if(!userUpdateRequest.getUserRole().equals(UserConstant.Admin_Role)){
            log.info("用户{}从{}权限修改为{}",userUpdateRequest.getId(),user.getUserRole(),userUpdateRequest.getUserRole());
        }
        BeanUtils.copyProperties(userUpdateRequest,user);
        boolean update = userService.updateById(user);
        ThrowUtil.throwIf(!update,new BusinessException(ErrorCode.SYSTEM_ERROR,"更新用户失败"));

        //4.获取用户并且更新
        return ResultUtil.success(update);
    }

    /**
     * 编辑用户信息(用户+管理员)
     * @param userEditRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> EditUser(@RequestBody UserEditRequest userEditRequest, HttpServletRequest httpServletRequest) {
        //1.参数校验
        ThrowUtil.throwIf(userEditRequest == null || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));

        //2.获取当前用户
        User loginUser = userService.getLoginUser(httpServletRequest);
        User user = new User();
        BeanUtils.copyProperties(userEditRequest,user);

        //3.检验禁止编辑部分
        user.setId(loginUser.getId());
        if(user.getUserName().length() >= 10) ThrowUtil.throwIf(true,new BusinessException(ErrorCode.PARAMS_ERROR,"用户名长度不能超过10"));
        if(user.getUserProfile().length() >= 100) ThrowUtil.throwIf(true,new BusinessException(ErrorCode.PARAMS_ERROR,"用户简介长度不能超过100"));

        //4.执行更新部分
        boolean updatedById = userService.updateById(user);
        ThrowUtil.throwIf(!updatedById,ErrorCode.OPERATION_ERROR,"更新用户信息失败");
        return ResultUtil.success(updatedById);
    }

    /**
     * 根据用户Id获取用户信息(管理员功能)
     * @param id
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/get")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<User> getUserById(long id,HttpServletRequest httpServletRequest) {
        //1.参数校验
        ThrowUtil.throwIf(id <= 0,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数非法"));
        //2.获取用户
        User user = userService.getById(id);
        ThrowUtil.throwIf(user == null,new BusinessException(ErrorCode.NOT_FOUND,"用户不存在"));
        return ResultUtil.success(user);
    }

    /**
     * 获取用户包装类
     * @param id
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVoById(long id,HttpServletRequest httpServletRequest) {
        //1.参数校验
        ThrowUtil.throwIf(id <= 0 || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数非法"));

        //2.获取用户包装类
        User user = userService.getById(id);
        ThrowUtil.throwIf(user == null,new BusinessException(ErrorCode.NOT_FOUND,"用户不存在"));
        UserVO userVO = userService.getUserVO(user);
        return ResultUtil.success(userVO);
    }

    /**
     * 分页获取用户列表(管理员功能)
     * @param userQueryRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest,HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(userQueryRequest == null || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        //2.获取分页查询条件
        long page = userQueryRequest.getPage();
        long size = userQueryRequest.getSize();
        //3.获取分页
        Page<User> userPage = userService.page(new Page<>(page, size), userService.getQueryWrapper(userQueryRequest));
        return ResultUtil.success(userPage);
    }

    /**
     * 分页获取用户封装列表
     * @param userQueryRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    //防止爬虫注解
    @RateLimiter(key = "listUserVoByPage",CountTime = 20,LimitCount = 20,timeUnit = RateIntervalUnit.SECONDS,limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Page<UserVO>> listUserVoByPage(@RequestBody UserQueryRequest userQueryRequest,HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(userQueryRequest == null || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        //2.获取分页参数
        Page<User> userPage = userService.page(new Page<>(userQueryRequest.getPage(), userQueryRequest.getSize())
                , userService.getQueryWrapper(userQueryRequest));
        //3.获取VO分页
        Page<UserVO> page = new Page<>(userQueryRequest.getPage(), userQueryRequest.getSize());
        Page<UserVO> userVOPage = page.setRecords(userService.getUserVOList(userPage.getRecords()));
        return ResultUtil.success(userVOPage);
    }

    /**
     * 更新自己信息
     * @param userEditRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateUserInfo(@RequestBody UserEditRequest userEditRequest,HttpServletRequest httpServletRequest) {
        //1.参数检验
        ThrowUtil.throwIf(userEditRequest == null || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));

        //2.获取更新数据
        User loginUser = userService.getLoginUser(httpServletRequest);
        User user = new User();
        BeanUtils.copyProperties(userEditRequest,user);
        //3.检验封装敏感信息
        user.setId(loginUser.getId());
        //4.更新数据
        boolean isUpdate = userService.updateById(user);
        ThrowUtil.throwIf(!isUpdate,new BusinessException(ErrorCode.SYSTEM_ERROR,"更新失败"));

        return ResultUtil.success(isUpdate);
    }

    /**
     * 用户签到
     * @param httpServletRequest
     * @return
     */
    @PostMapping("add/sign_in")
    public BaseResponse<Boolean> addUserSignIn(HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        //2.获取签到情况
        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean isSignIn = userService.addUserSignIn(loginUser.getId());
        return ResultUtil.success(isSignIn);
    }

    /**
     * 获取年签到记录
     * @param year
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/get/sign_in")
    public BaseResponse<List<Integer>> getSignInList(Integer year,HttpServletRequest httpServletRequest) {
        ThrowUtil.throwIf(httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        User loginUser = userService.getLoginUser(httpServletRequest);
        List<Integer> userSignInYear = userService.getUserSignInYear(loginUser.getId(), year);
        return ResultUtil.success(userSignInYear);
    }
}

package project.controller;

import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.model.dto.user.UserAddRequest;
import project.model.dto.user.UserLoginRequest;
import project.model.dto.user.UserRegisterRequest;
import project.model.entity.User;
import project.model.vo.UserVO;
import project.model.vo.loginUserVO;
import project.service.UserService;
import project.utils.PasswordUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Controller
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
        ThrowUtil.throwIf(userPassword.equals(checkPassword),ErrorCode.PARAMS_ERROR,"两次密码不一致");
        long result = userService.UserRegister(userAccount,userPassword,checkPassword);
        return ResultUtil.success(result);
    }

    /**
     * 用户登录
     * @param userLoginRequest
     * @param httpServletRequest
     * @return
     */
    @RequestMapping("/login")
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
        userService.login(userAccount,userPassword,httpServletRequest);
        //4.返回结果
        return null;
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
    @GetMapping("/get/loginUser")
    public BaseResponse<loginUserVO> getLoginUserVO(HttpServletRequest httpServletRequest) {
        ThrowUtil.throwIf(httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtil.success(userService.getLoginUserVO(loginUser));
    }

    @PostMapping("/add/user")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest,HttpServletRequest httpServletRequest){
        //1.参数校验
        ThrowUtil.throwIf(userAddRequest == null || httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));

        //2.用户参数校验
        if(userAddRequest.getUserAccount().length() < 4 || userAddRequest.getUserAccount().length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号格式错误");
        }
        if(!StrUtil.containsAnyIgnoreCase(userAddRequest.getUserAccount(),"admin","user")) {
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

    @GetMapping("/get/sign_in")
    public BaseResponse<List<Integer>> getSignInList(Integer year,HttpServletRequest httpServletRequest) {
        ThrowUtil.throwIf(httpServletRequest == null,new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空"));
        User loginUser = userService.getLoginUser(httpServletRequest);
        List<Integer> userSignInYear = userService.getUserSignInYear(loginUser.getId(), year);
        return ResultUtil.success(userSignInYear);
    }
}

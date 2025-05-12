package project.service.Imp;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.lettuce.core.RedisException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.constant.CommonConstant;
import project.constant.RedisConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.mapper.UserMapper;
import project.model.dto.user.UserQueryRequest;
import project.model.entity.User;
import project.model.enums.UserRoleEnum;
import project.model.vo.UserVO;
import project.model.vo.loginUserVO;
import project.satoken.DeviceUtil;
import project.service.UserService;
import project.utils.PasswordUtils;
import project.utils.SqlUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static project.constant.UserConstant.User_Status_Login;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 用户实现类
 */

@Service
@Slf4j
public class UserServiceImp extends ServiceImpl<UserMapper, User> implements UserService  {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 账号注册功能实现
     * @param userAccount
     * @param userPassword
     * @param checkPassword
     * @return
     */
    @Override
    public Long UserRegister(String userAccount, String userPassword, String checkPassword) {
        //分布式锁主键
//        String LockKey = "register_Lock"+userAccount;

        //1.校验账户格式
        if(userAccount.length() < 4 || userAccount.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号格式错误");
        }
        //2.校验密码格式
        if(userPassword.length() < 8 || userPassword.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码格式错误");
        }
        //3.查询账户是否重复
//        synchronized (userAccount.intern())
//        try
//        {
//            boolean tryLock = redissonClient.getLock(LockKey).tryLock(1, 5, TimeUnit.SECONDS);
//            if(!tryLock){
//                throw new BusinessException(ErrorCode.OPERATION_ERROR,"系统繁忙");
//            }
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount",userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if(count > 0 )
            {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户账号已存在");
            }
            //4.密码加密
            String hashPassword = PasswordUtils.hashPassword(userPassword);
            //5.注册用户
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(hashPassword);
            boolean save = this.save(user);
  //          int insert = this.baseMapper.insert(user);
            if(!save) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户注册失败");
            }
            return user.getId();
//        } catch (InterruptedException e) {
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"在执行账号注册时被打断"+e.getMessage());
//        } finally {
//            redissonClient.getLock(LockKey).unlock();
//        }
    }

    /**
     * 用户登录
     * @param userAccount
     * @param userPassword
     * @param httpServletRequest
     * @return
     */
    @Override
    public loginUserVO login(String userAccount, String userPassword, HttpServletRequest httpServletRequest) {
        //1.参数校验
        ThrowUtil.throwIf(StringUtils.isAnyBlank(userAccount,userPassword),ErrorCode.PARAMS_ERROR,"请输入账号或者密码");

        //2.查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);
        User user = this.baseMapper.selectOne(queryWrapper);
        if(user == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户不存在");
        }
        boolean checked= PasswordUtils.checkPassword(userPassword, user.getUserPassword());
        //2.1密码校验
        if(!checked) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码错误");
        }
        //2.2封号校验
        if(UserRoleEnum.BAN.getValue().equals(user.getUserRole())){
            throw new BusinessException(ErrorCode.NOT_ROLE,"该账号已被封禁");
        }

        //3.获取登录设备并判断冲突
        StpUtil.login(user.getId(), DeviceUtil.getDeviceType(httpServletRequest));
        StpUtil.getSession().set(User_Status_Login,user);
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户
     * @param httpServletRequest
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest httpServletRequest) {
        //1.判断用户是否登录
        Object loginIdDefaultNull = StpUtil.getLoginIdDefaultNull();
        ThrowUtil.throwIf(loginIdDefaultNull == null, ErrorCode.NOT_LOGIN,"用户未登录");

        //2.获取用户信息
        User user = this.getById((String) loginIdDefaultNull);
        ThrowUtil.throwIf(user == null, ErrorCode.NOT_LOGIN,"用户不存在");

        return user;
    }

    /**
     * 获取当前登录用户(允许未登录)
     * @param httpServletRequest
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest httpServletRequest) {
        //1.判断用户是否登录
        Object loginIdDefaultNull = StpUtil.getLoginIdDefaultNull();
        ThrowUtil.throwIf(loginIdDefaultNull == null, ErrorCode.NOT_LOGIN,"用户未登录");

        User user = this.getById((Long) loginIdDefaultNull);
        return user;
    }

    /**
     * 是否为管理员
     * @param httpServletRequest
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest httpServletRequest) {
        //1/获取当前用户
        Object object = StpUtil.getSession().get(User_Status_Login);
        User user = (User) object;

        //2.1用户校验
        ThrowUtil.throwIf(user == null, ErrorCode.NOT_LOGIN,"用户不存在");
        //2.2权限校验
        return user.getUserRole().equals(UserRoleEnum.ADMIN.getValue());
    }

    /**
     * 用户注销
     * @param httpServletRequest
     * @return
     */
    @Override
    public boolean userLogOut(HttpServletRequest httpServletRequest) {
        StpUtil.checkLogin();
        //登出
        StpUtil.logout();
        return true;
    }

    /**
     * 获取当前登录用户信息(脱敏)
     * @param user
     * @return
     */
    @Override
    public loginUserVO getLoginUserVO(User user) {
        //1.参数校验
        ThrowUtil.throwIf(user == null, ErrorCode.PARAMS_ERROR,"获取用户信息失败");

        //2.获取脱敏类
        loginUserVO loginUserVO = new loginUserVO();
        BeanUtils.copyProperties(user,loginUserVO);

        //3.返回数据
        ThrowUtil.throwIf(loginUserVO == null, ErrorCode.OPERATION_ERROR,"获取用户信息失败");
        return loginUserVO;
    }

    /**
     * 获取用户信息(脱敏)
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        //1.参数校验
        ThrowUtil.throwIf(user == null, ErrorCode.PARAMS_ERROR,"获取用户信息失败");

        //2.数据脱敏
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);

        //3.返回数据
        return  userVO;
    }

    /**
     * 获取用户信息(脱敏)
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        //1.参数校验
        if(CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        //2.数据脱敏
         return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userqueryrequest) {
        //1.参数校验
        ThrowUtil.throwIf(userqueryrequest == null, ErrorCode.PARAMS_ERROR,"查询条件不能为空");

        //2.获取QueryWrapper
        Long id = userqueryrequest.getId();
        String unionId = userqueryrequest.getUnionId();
        String mpOpenId = userqueryrequest.getMpOpenId();
        String userName = userqueryrequest.getUserName();
        String userProfile = userqueryrequest.getUserProfile();
        String userRole = userqueryrequest.getUserRole();
        String sortOrder = userqueryrequest.getSortOrder();
        String sortField = userqueryrequest.getSortField();
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq(id != null, "id", id);
        userQueryWrapper.eq(StrUtil.isNotBlank(unionId), "unionId", unionId);
        userQueryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        userQueryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        userQueryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        userQueryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        userQueryWrapper.orderBy(SqlUtil.validSortField(sortField), sortOrder.equals(CommonConstant.Sort_Order_DESC), sortField);

        //3.返回结果
        return userQueryWrapper;
    }

    /**
     * 添加用户签到记录
     * @param userId
     * @return
     */
    @Override
    public boolean addUserSignIn(long userId) {
        //1.参数校验
        if(userId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户id不能为空");
        }

        //2.执行签到操作
        LocalDate now = LocalDate.now();
        //获取用户签到册
        String userSignInKey = RedisConstant.getUserSignInKey(now.getYear(), userId);
        RBitSet bitSet = redissonClient.getBitSet(userSignInKey);
        //获取当前日期
        int offest = now.getDayOfYear();
        //用户签到
        if(!bitSet.get(offest)) {
            bitSet.set(offest);
        }
        return true;
    }

    /**
     * 获取用户某年的签到记录
     * @param userId
     * @param year
     * @return
     */
    @Override
    public List<Integer> getUserSignInYear(long userId, Integer year) {
        //1.参数校验
        if(userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户id不能为空");
        }
        if(year == null){
            year  = LocalDate.now().getYear();
        } else if (year <RedisConstant.MinYear) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"年份不能小于" + RedisConstant.MinYear);
        }

        //2.获取某年的签到册
        String userSignInKey = RedisConstant.getUserSignInKey(year, userId);
        RBitSet rbitSet = redissonClient.getBitSet(userSignInKey);
        ArrayList<Integer> DaySignList = null;

        try {
            //3.1将数据从RBitSet中全部获取
            int NowDay  = (year == LocalDate.now().getYear()) ? LocalDate.now().getDayOfYear()
                    :(Year.of(year).isLeap() ? 366 : 365);
            byte[] byteArray = rbitSet.toByteArray();
            if(byteArray == null || byteArray.length == 0) {
                return Collections.emptyList();
            }
            DaySignList = new ArrayList<>();
            BitSet set = BitSet.valueOf(byteArray);
            //3.2操作Byte数组
            for (int i = set.nextSetBit(0); i >= 0 && i < NowDay; i = set.nextSetBit(i + 1)) {
                DaySignList.add(i);
            }
        } catch (BusinessException e)
        {
            log.error("获取用户签到记录失败",e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"获取用户签到记录失败");
        }
        return DaySignList;
    }

}



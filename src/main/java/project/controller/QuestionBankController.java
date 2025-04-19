package project.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.poi.ss.formula.functions.T;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import project.annotation.RateLimiter;
import project.common.BaseResponse;
import project.common.DeleteRequest;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.ThrowUtil;
import project.model.dto.question.QuestionQueryRequest;
import project.model.dto.questionBank.QuestionBankAddRequest;
import project.model.dto.questionBank.QuestionBankQueryRequest;
import project.model.dto.questionBank.QuestionBankUpdateRequest;
import project.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import project.model.entity.Question;
import project.model.entity.QuestionBank;
import project.model.entity.QuestionBankQuestion;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.model.vo.QuestionBankVO;
import project.model.vo.QuestionVO;
import project.model.vo.UserVO;
import project.sentinel.SentinelConstant;
import project.service.QuestionBankQuestionService;
import project.service.QuestionBankService;
import project.service.QuestionService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题库接口
 */
@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionService questionService;

    /**
     * 添加题库(管理员功能)
     * @param addRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest addRequest, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(ObjectUtils.isEmpty(addRequest) || request == null, ErrorCode.PARAMS_ERROR,"参数不可以为空");
        //2.1类型转换
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(addRequest,questionBank);
        User loginUser = userService.getLoginUser(request);
        questionBank.setUserId(loginUser.getId());
        questionBank.setCreateTime(new Date());
        questionBank.setIsDelete(0);
        //2.2数据校验
        questionBankService.validQuestionBank(questionBank,true);
        //3.添加题库
        boolean saved = questionBankService.save(questionBank);
        ThrowUtil.throwIf(!saved, ErrorCode.OPERATION_ERROR,"添加失败");
        return ResultUtil.success(questionBank.getId());
    }

    /**
     * 删除题库(管理员功能
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/remove")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> removeQuestionBank(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        //1.参数校验
        Long requestId = deleteRequest.getId();
        ThrowUtil.throwIf(ObjectUtils.isEmpty(deleteRequest) || requestId <= 0, ErrorCode.PARAMS_ERROR,"参数不可以为空");
        //2.题库是否存在
        QuestionBank questionBank = questionBankService.getById(requestId);
        ThrowUtil.throwIf(questionBank == null, ErrorCode.NOT_FOUND,"题库不存在");
        //Todo 如果添加@Resource对于QBQ会出现循环依赖,怎么处理
        //3.删除题库题目关联表
        boolean remove = questionBankService.removeById(deleteRequest.getId());
        ThrowUtil.throwIf(!remove, ErrorCode.OPERATION_ERROR,"删除题目题库关联失败");
        //4.删除题库
        boolean removed = questionBankService.removeById(requestId);
        ThrowUtil.throwIf(!removed, ErrorCode.OPERATION_ERROR,"删除题库失败");
        return ResultUtil.success(true);
    }

    /**
     * 更新题库(管理员功能)
     * @param updateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Long> updateQuestionBank(@RequestBody QuestionBankUpdateRequest updateRequest, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(ObjectUtils.isEmpty(updateRequest) || request == null, ErrorCode.PARAMS_ERROR,"参数不可以为空");
        //2.转化为实体
        QuestionBank questionBank = questionBankService.getById(updateRequest.getId());
        ThrowUtil.throwIf(questionBank == null, ErrorCode.NOT_FOUND,"题库不存在");
        BeanUtils.copyProperties(updateRequest,questionBank);
        //3.数据校验
        //Todo validQuestionBank是否可以优化
        User loginUser = userService.getLoginUser(request);
        questionBankService.validQuestionBank(questionBank,false);
        questionBank.setUpdateTime(new Date());
        //4.更新题库
        boolean save = questionBankService.save(questionBank);
        ThrowUtil.throwIf(!save, ErrorCode.OPERATION_ERROR,"更新失败");

        return ResultUtil.success(questionBank.getId());
    }

    /**
     * 获取题库封装
     * @param queryRequest
     * @param request
     * @return
     */
    @GetMapping("get/VO")
    public BaseResponse<QuestionBankVO> getQuestionBankVO(@RequestBody QuestionBankQueryRequest queryRequest,HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(ObjectUtils.isEmpty(queryRequest) || request == null, ErrorCode.PARAMS_ERROR,"参数不可以为空");
        Long id = queryRequest.getId();
        //2.1获取key
        String key = "bank:detail:"+id;
        //2.2查询缓存
        if(JdHotKeyStore.isHotKey(key)){
            Object questionBankVO = JdHotKeyStore.get(key);
            if(questionBankVO != null){
                return ResultUtil.success((QuestionBankVO) questionBankVO);
            }
        }
        //3.查询数据库
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtil.throwIf(questionBank == null, ErrorCode.NOT_FOUND,"题库不存在");
        //4.封装数据
        QuestionBankVO questionBankVO = QuestionBankVO.objToVo(questionBank);
        //5.数据补充
        //5.1用户信息补充
        User user = userService.getById(questionBank.getUserId());
        questionBankVO.setUser(userService.getUserVO(user));
        //5.2是否需要补充题目
        if (!queryRequest.isNeedQueryQuestionList()) {
            return ResultUtil.success(questionBankVO);
        }
        //5.3题目信息补充
        QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
        questionQueryRequest.setQuestionBankId(id);
        questionQueryRequest.setPage(questionQueryRequest.getPage());
        queryRequest.setTitle(questionQueryRequest.getTitle());
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        Page<QuestionVO> questionVOPage = questionService.getQuestionVOPage(questionPage, request);
        questionBankVO.setQuestionPage(questionVOPage);
        //5.4设置本地缓存
        JdHotKeyStore.smartSet(key, questionBankVO);

        return ResultUtil.success(questionBankVO);
    }

    /**
     * 分页查询题库(管理员功能)
     * @param queryRequest
     * @param request
     * @return
     */
    @GetMapping("/page")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Page<QuestionBank>> listQuestionBank(@RequestBody QuestionBankQueryRequest queryRequest, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(ObjectUtils.isEmpty(queryRequest) || request == null, ErrorCode.PARAMS_ERROR,"参数不可以为空");
        //2.分页查询
        int page = queryRequest.getPage();
        int size = queryRequest.getSize();
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(page, size)
                , questionBankService.getQueryWrapper(queryRequest));
        return ResultUtil.success(questionBankPage);
    }

    /**
     * 分页获取题库封装
     * @param queryRequest
     * @param request
     * @return
     */
    @GetMapping("/page/VO")
    @RateLimiter(key = "listQuestionBanVO",CountTime = 1,LimitCount = 5,timeUnit = RateIntervalUnit.SECONDS,limitType = LimitTypeEnum.REJECT_USER)
    @SentinelResource(value = SentinelConstant.ListQuestionBankVoByPage,blockHandler = "handleBlockException",fallback = "handleFallback")
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVO(@RequestBody QuestionBankQueryRequest queryRequest, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(ObjectUtils.isEmpty(queryRequest) || request == null, ErrorCode.PARAMS_ERROR,"参数不可以为空");
        //2.查询数据库
        int page = queryRequest.getPage();
        int size = queryRequest.getSize();
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(page, size)
                , questionBankService.getQueryWrapper(queryRequest));
        //走本地缓存获取
        return ResultUtil.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
    }

    /**
     * 本地限流策略
     * @param request
     * @param queryRequest
     * @param ex
     * @return
     */
    public BaseResponse<Page<QuestionBankVO>> handleBlockException(@RequestBody QuestionBankQueryRequest queryRequest
            , HttpServletRequest request, BlockException ex){
        //1.降级操作
        if(ex instanceof DegradeException){
            return handleFallback(queryRequest,request,ex);
        }
        return ResultUtil.error(ErrorCode.SYSTEM_ERROR,"系统压力过大,请等待");
    }

    /**
     * 降级策略
     * @param questionBankQueryRequest
     * @param request
     * @param ex
     * @return
     */
    public BaseResponse<Page<QuestionBankVO>> handleFallback(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                             HttpServletRequest request, Throwable ex) {
        return ResultUtil.success(null);
    }
}

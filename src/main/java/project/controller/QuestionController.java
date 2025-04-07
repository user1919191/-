package project.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import net.sf.jsqlparser.statement.Block;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import project.annotation.RateLimiter;
import project.common.BaseResponse;
import project.common.DeleteRequest;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.model.dto.question.*;
import project.model.entity.Question;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.model.vo.QuestionVO;
import project.sentinel.SentinelConstant;
import project.service.QuestionBankQuestionService;
import project.service.QuestionService;
import project.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.csp.sentinel.EntryType;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

@RequestMapping("/question")
@RestController
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    /**
     * 创建题目(管理员功能)
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/create")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Long> createQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(questionAddRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.数据校验
        //2.1将Request转为实体
        Question question = new Question();
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        BeanUtils.copyProperties(questionAddRequest, question);
        questionService.validQuestion(question, true);
        //2.2填充敏感数据
        question.setCreateTime(new Date());
        question.setUserId(userService.getLoginUser(request).getId());
        //3.保存到数据库
        boolean save = questionService.save(question);
        ThrowUtil.throwIf(!save, ErrorCode.SYSTEM_ERROR,"保存失败");
        //.返回新ID
        return ResultUtil.success(question.getId());
    }

     /**
     * 删除题目(管理员功能)
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> deleteQuestion(DeleteRequest deleteRequest, HttpServletRequest request)
    {
        ThrowUtil.throwIf(deleteRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        Long deleteRequestId = deleteRequest.getId();
        //1.参数校验
        ThrowUtil.throwIf(deleteRequestId == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.1权限再次校验
        Question question = questionService.getById(deleteRequestId);
        ThrowUtil.throwIf(question == null || question.getUserId() != null, ErrorCode.PARAMS_ERROR,"题目不存在");
        Long id = userService.getLoginUser(request).getId();
        if(!question.getUserId().equals(id) || !userService.isAdmin(request)){
            throw new BusinessException(ErrorCode.NOT_ROLE,"您没有删除题目权限");
        }
        //2.2从数据库中删除
        question.setIsDelete(1);
        question.setEditTime(new Date());
        question.setCloseChangeId(id);
        boolean save = questionService.save(question);
        ThrowUtil.throwIf(!save, ErrorCode.OPERATION_ERROR,"删除失败");
        return ResultUtil.success(true);
    }

    /**
     * 更新题目(管理员功能)
     * @param questionUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Long> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest,HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(questionUpdateRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //3.数据校验
        Question question = questionService.getById(questionUpdateRequest.getId());
        ThrowUtil.throwIf(question == null, ErrorCode.PARAMS_ERROR,"题目不存在");
        //3.1将Request转为实体
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        BeanUtils.copyProperties(questionUpdateRequest, question);
        //3.2敏感数据封装
        question.setEditTime(new Date());
        question.setCloseChangeId(userService.getLoginUser(request).getId());
        //3.3内容数据校验
        questionService.validQuestion(question, false);
        //4.保存到数据库
        boolean save = questionService.save(question);
        ThrowUtil.throwIf(!save, ErrorCode.SYSTEM_ERROR,"保存失败");

        return ResultUtil.success(question.getId());
    }

    /**
     * 根据Id查询题目封装
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/select/id")
    @RateLimiter(key = "selectQuestionById", CountTime = 10, LimitCount = 10, timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<QuestionVO> selectQuestionById(long id,HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(request == null,ErrorCode.PARAMS_ERROR,"参数不能为空");
        ThrowUtil.throwIf(id <= 0, ErrorCode.PARAMS_ERROR,"查询题目ID不合法");
        //2.查询题目
        Question question = questionService.getById(id);
        ThrowUtil.throwIf(question == null, ErrorCode.PARAMS_ERROR,"题目不存在");

        return ResultUtil.success(questionService.getQuestionVO(question,request));
    }

    /**
     * 分页获取题目列表(管理员可用)
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/select/page")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Page<Question>> getQuestionPage(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request){
        ThrowUtil.throwIf(questionQueryRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return ResultUtil.success(questionPage);
    }

    /**
     * 分页获取题目封装列表
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/select/page/vo")
    @RateLimiter(key = "selectQuestionVOPage", CountTime = 10, LimitCount = 10, timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Page<QuestionVO>> getQuestionVOPage(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request){
        ThrowUtil.throwIf(questionQueryRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //1.使用Sentinel限流
        //1.1获取IP
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        //1.2基于IP限流
        try{
            entry = SphU.entry(SentinelConstant.ListQuestionVoByPage,EntryType.IN,1,remoteAddr);
            //1.3查寻数据库
            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
            return ResultUtil.success(questionService.getQuestionVOPage(questionPage, request));
        } catch (Throwable ex) {
            if(ex instanceof BlockException){
                //1.4降级操作
               if(ex instanceof DegradeException){
                   //Todo 可以返回本地数据
                   return ResultUtil.success(null);
               }
               //1.5限流操作
                return  ResultUtil.error(ErrorCode.NOT_FOUND,"访问过于频繁，请稍后再试");
            }
            Tracer.trace(ex);
            return ResultUtil.error(ErrorCode.SYSTEM_ERROR,"系统异常");
        }finally {
            if(entry != null){
                entry.exit(1,remoteAddr);
            }
        }
    }

    /**
     * 分页获取当前用户创建的题目列表
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/select/page/user")
    @RateLimiter(key = "selectQuestionVOPageByUser", CountTime = 10, LimitCount = 10, timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Page<QuestionVO>> getQuestionPageCreateByUser(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(questionQueryRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.校验查询条件
        questionQueryRequest.setUserId(userService.getLoginUser(request).getId());
        int page = questionQueryRequest.getPage();
        int size = questionQueryRequest.getSize();
        //3.查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(page, size), questionService.getQueryWrapper(questionQueryRequest));
        //4.封装返回
        return ResultUtil.success(questionService.getQuestionVOPage(questionPage,request));
    }

    /**
     * 更新银狐创建的题目(创建用户使用)
     * @param editRequest
     * @param request
     * @return
     */
    @PostMapping("/update/user")
    @RateLimiter(key = "updateQuestionByUser", CountTime = 10, LimitCount = 10, timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Boolean> updateQuestionCreateByUser(@RequestBody QuestionEditRequest editRequest,HttpServletRequest request){
        //1.参数校验
       ThrowUtil.throwIf(editRequest == null || editRequest.getId() <= 0 ||request == null, ErrorCode.PARAMS_ERROR,"参数非法");
        //2.从数据库获取题目
        Question question = questionService.getById(editRequest.getId());
        ThrowUtil.throwIf(question == null, ErrorCode.NOT_FOUND,"题目不存在");
        //3.权限校验
        if(!question.getUserId().equals(userService.getLoginUser(request).getId())
                || !userService.getLoginUser(request).getUserRole().equals(UserConstant.Default_Role)){
            if(userService.getLoginUser(request).getUserRole().equals(UserConstant.Admin_Role)){
                throw new BusinessException(ErrorCode.NOT_VISIT,"管理员请使用管理渠道修改题目");
            }
            throw new BusinessException(ErrorCode.NOT_ROLE,"没有权限修改题目");
        }
        //5.更新题目
        BeanUtils.copyProperties(editRequest,question);
        //4.敏感数据校验
        questionService.validQuestion(question,false);
        //6.保存到数据库
        boolean save = questionService.save(question);
        ThrowUtil.throwIf(!save, ErrorCode.SYSTEM_ERROR,"更新失败");
        return  ResultUtil.success(true);
    }

    /**
     * 从ES中获取题目(Sentinel限流)
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/search/page/vo")
    @RateLimiter(key = "searchQuestionVOByPage", CountTime = 10, LimitCount = 10, timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request) {
        long size = questionQueryRequest.getSize();

        //1.限流开启
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelConstant.ListQuestionVoByPage, EntryType.IN, 1, remoteAddr);
            //2.查询数据
            //2.1查询 ES
            Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
            return ResultUtil.success(questionService.getQuestionVOPage(questionPage, request));
        }catch (Exception ex){
            if(ex instanceof BlockException){
                //3.1降级方案
                if(ex instanceof DegradeException){
                    //2.2从数据库中获取
                    Page<Question> SQLquestionPage = questionService.listQuestionByPage(questionQueryRequest);
                    return ResultUtil.success(questionService.getQuestionVOPage(SQLquestionPage, request));
                }
                //3.2限流方案
                return ResultUtil.error(ErrorCode.SYSTEM_ERROR,"系统繁忙，请稍后再试");
            }else{
                return ResultUtil.error(ErrorCode.SYSTEM_ERROR,"系统错误");
            }
        }finally {
            if(entry != null){
                entry.exit(1,remoteAddr);
            }
        }
    }

    /**
     * 批量删除题目(管理员使用)
     * @param questionBatchDeleteRequest
     * @return
     */
    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest) {
        //1.参数校验
        ThrowUtil.throwIf(questionBatchDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtil.throwIf(questionBatchDeleteRequest.getQuestionIdList().size()<=0, ErrorCode.PARAMS_ERROR, "请选择要删除的题目");
        //2.批量删除
        questionService.batchDeleteQuestions(questionBatchDeleteRequest.getQuestionIdList());
        return ResultUtil.success(true);
    }

    /**
     * AI 生成题目（仅管理员可用）
     * @param questionAIGenerateRequest
     * @param request
     * @return
     */
    @PostMapping("/ai/generate/question")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> aiGenerateQuestions(@RequestBody QuestionAIGenerateRequest questionAIGenerateRequest, HttpServletRequest request) {
        String questionType = questionAIGenerateRequest.getQuestionType();
        int number = questionAIGenerateRequest.getNumber();
        //1.校验参数
        ThrowUtil.throwIf(StrUtil.isBlank(questionType), ErrorCode.PARAMS_ERROR, "题目类型不能为空");
        ThrowUtil.throwIf(number <= 0, ErrorCode.PARAMS_ERROR, "题目数量必须大于 0");
        //2.获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        //3.调用 AI 生成题目服务
        questionService.aiGenerateQuestions(questionType, number, loginUser);
        return ResultUtil.success(true);
    }
}

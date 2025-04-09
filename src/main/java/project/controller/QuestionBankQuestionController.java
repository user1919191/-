package project.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import project.annotation.RateLimiter;
import project.common.BaseResponse;
import project.common.DeleteRequest;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.ThrowUtil;
import project.mapper.QuestionBankQuestionMapper;
import project.model.dto.questionBankQuestion.*;
import project.model.entity.QuestionBankQuestion;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.model.vo.QuestionBankQuestionVO;
import project.model.vo.QuestionVO;
import project.service.QuestionBankQuestionService;
import project.service.QuestionService;
import project.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/questionBankQuestion")
@Slf4j
public class QuestionBankQuestionController {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private UserService userService;

    /**
     * 向题库中添加题目(管理员功能)
     * @param questionBankQuestionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Long> addQuestionBankQuestion(@RequestBody QuestionBankQuestionAddRequest questionBankQuestionAddRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(questionBankQuestionAddRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.封装为实体类
        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
        BeanUtils.copyProperties(questionBankQuestionAddRequest,questionBankQuestion);
        questionBankQuestion.setCreateTime(new Date());
        Long id = userService.getLoginUser(request).getId();
        questionBankQuestion.setUserId(id);
        //3.数据内容校验
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion,true);
        //4.写入数据库
        questionBankQuestionService.save(questionBankQuestion);
        //5.返回ID
        return ResultUtil.success(questionBankQuestion.getId());
    }

    /**
     * 从题库中移除题目
     * @param removeRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> removeQuestionBankQuestion(@RequestBody DeleteRequest removeRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(removeRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        Long removeRequestId = removeRequest.getId();
        QuestionBankQuestion removeQuestion = questionBankQuestionService.getById(removeRequestId);
        ThrowUtil.throwIf(removeQuestion == null, ErrorCode.PARAMS_ERROR,"要删除的题目Id或题库Id为空");
        //2.权限校验
        User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(!loginUser.getId().equals(removeQuestion.getUserId()) || !loginUser.getUserRole().equals(UserConstant.Admin_Role), ErrorCode.NOT_ROLE,"权限不足");
        //3.执行删除操作
        boolean removeSuccess = questionBankQuestionService.removeById(removeRequestId);
        ThrowUtil.throwIf(!removeSuccess, ErrorCode.PARAMS_ERROR,"删除失败");
        return ResultUtil.success(true);
    }

    /**
     * 更新题目题库关联(管理员功能)
     * @param updateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Long> updateQuestionBankQuestion(@RequestBody QuestionBankQuestionUpdateRequest updateRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(updateRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.1获取权限
        User loginUser = userService.getLoginUser(request);
        //2.2数据内容校验
        QuestionBankQuestion questionBankQuestion = questionBankQuestionService.getById(updateRequest.getId());
        ThrowUtil.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR,"要更新的题目不存在");
        ThrowUtil.throwIf(!loginUser.getUserRole().equals(UserConstant.Admin_Role) ||
                !questionBankQuestion.getUserId().equals(loginUser.getId()), ErrorCode.NOT_ROLE,"权限不足");
        QuestionBankQuestion bankQuestion = questionBankQuestionService.getById(updateRequest.getId());
        ThrowUtil.throwIf(!bankQuestion.getId().equals(questionBankQuestion.getId()), ErrorCode.PARAMS_ERROR,"要更新的题目不存在");
        BeanUtils.copyProperties(updateRequest,bankQuestion);
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion,false);
        //3.执行更新操作
        boolean update = questionBankQuestionService.updateById(bankQuestion);
        ThrowUtil.throwIf(!update, ErrorCode.PARAMS_ERROR,"更新失败");
        return ResultUtil.success(bankQuestion.getId());
    }

    /**
     * 根据Id湖区题目封装
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/VO")
    public BaseResponse<QuestionBankQuestionVO> getQuestionBankQuestionVOById(long id,HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(id <= 0 || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.获取题目
        QuestionBankQuestion questionBankQuestion = questionBankQuestionService.getById(id);
        ThrowUtil.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR,"题目不存在");
        //3.转为封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);
        ThrowUtil.throwIf(questionBankQuestionVO == null, ErrorCode.PARAMS_ERROR,"题目获取失败");
        return ResultUtil.success(questionBankQuestionVO);
    }

    /**
     * 分页获取题目(管理员功能)
     * @param queryRequest
     * @param request
     * @return
     */
    @GetMapping("/get/page")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse <Page<QuestionBankQuestion>> getQuestionBankQuestionPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(queryRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.构造分页参数
        int page = queryRequest.getPage();
        int size = queryRequest.getSize();
        //3.分页获取
        Page<QuestionBankQuestion> pageQBQ = questionBankQuestionService.page(new Page<>(page,size),questionBankQuestionService.getQueryWrapper(queryRequest));

        return ResultUtil.success(pageQBQ);
    }

    /**
     * 分页获取题目封装
     * @param queryRequest
     * @param request
     * @return
     */
    @GetMapping("/get/page/VO")
    @RateLimiter(key = "getQuestionBankQuestionVOPage", CountTime = 60 , LimitCount = 30 ,timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Page<QuestionBankQuestionVO>> getQuestionBankQuestionVOPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(queryRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.获取分页
        int page = queryRequest.getPage();
        int size = queryRequest.getSize();
        Page<QuestionBankQuestion> questionBankQuestionPage = questionBankQuestionService
                .page(new Page<>(page, size), questionBankQuestionService.getQueryWrapper(queryRequest));
        //3.转化为封装
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = questionBankQuestionService
                .getQuestionBankQuestionVOPage(questionBankQuestionPage, request);
        ThrowUtil.throwIf(questionBankQuestionVOPage == null, ErrorCode.PARAMS_ERROR,"题目获取失败");
        return ResultUtil.success(questionBankQuestionVOPage);
    }

    /**
     * 分页获取当前用户创建的题目列表
     * @param queryRequest
     * @param request
     * @return
     */
    @GetMapping("/get/page/VO/user")
    @RateLimiter(key = "getQuestionBankQuestionVOPageByUser", CountTime = 10 , LimitCount = 10 ,timeUnit = RateIntervalUnit.SECONDS, limitType = LimitTypeEnum.REJECT_USER)
    public BaseResponse<Page<QuestionBankQuestionVO>> getQuestionBankQuestionVOPageByUser(@RequestBody QuestionBankQuestionQueryRequest queryRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(queryRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.数据校验
        User loginUser = userService.getLoginUser(request);
        queryRequest.setUserId(loginUser.getId());
        int page = queryRequest.getPage();
        int size = queryRequest.getSize();
        //3.获取分页
        Page<QuestionBankQuestion> questionBankQuestionPage = questionBankQuestionService.page(new Page<>(page, size),
                questionBankQuestionService.getQueryWrapper(queryRequest));
        if(questionBankQuestionPage.getTotal() == 0) {
            return ResultUtil.success(new Page<QuestionBankQuestionVO>(page, size));
        }
        //4.转化为封装
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = questionBankQuestionService
                .getQuestionBankQuestionVOPage(questionBankQuestionPage, request);

        return ResultUtil.success(questionBankQuestionVOPage);
    }

    /**
     * 删除题库题目关联(管理员功能)
     * @param removeRequest
     * @param request
     * @return
     */
    @PostMapping("/remove")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> removeQuestionBankQuestion(@RequestBody QuestionBankQuestionRemoveRequest removeRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(removeRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.数据校验
        Long questionId = removeRequest.getQuestionId();
        Long questionBankId = removeRequest.getQuestionBankId();
        ThrowUtil.throwIf(questionBankId <= 0 || questionId <= 0, ErrorCode.PARAMS_ERROR,"要删除的题目或者题库不合法");
        //3.删除执行
        LambdaQueryWrapper<QuestionBankQuestion> questionBankQuestionLambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .select(QuestionBankQuestion::getId)
                .eq(QuestionBankQuestion::getQuestionId, questionId)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
        boolean removed = questionBankQuestionService.remove(questionBankQuestionLambdaQueryWrapper);
        ThrowUtil.throwIf(!removed, ErrorCode.PARAMS_ERROR,"删除失败");
        //3.2保存日志
        log.info("管理员{}删除题库:{},题目:{}关联成功",userService.getLoginUser(request).getId(),questionBankId,questionId);
        return ResultUtil.success(true);
    }

    /**
     * 批量添加题目到题库(管理员功能)
     * @param batchAddRequest
     * @param request
     * @return
     */
    @PostMapping("add/batch")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> batchAddQuestionToBank(@RequestBody QuestionBankQuestionBatchAddRequest batchAddRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(batchAddRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.数据校验
        Long questionBankId = batchAddRequest.getQuestionBankId();
        List<Long> questionIds = batchAddRequest.getQuestionIdList();
        //3.执行添加
        questionBankQuestionService.batchAddQuestionsToBank(questionIds,questionBankId,userService.getLoginUser(request));
        return ResultUtil.success(true);
    }

    /**
     * 批量删除题目题库关联(挂管理员功能)
     * @param removeRequest
     * @param request
     * @return
     */
    @PostMapping("/remove/batch")
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> batchRemoveQuestionToBank(@RequestBody QuestionBankQuestionBatchRemoveRequest removeRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(removeRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        //2.执行删除
        Long questionBankId = removeRequest.getQuestionBankId();
        List<Long> questionIdList = removeRequest.getQuestionIdList();
        questionBankQuestionService.batchRemoveQuestionsFromBank(questionIdList,questionBankId);
        return ResultUtil.success(true);
    }
}

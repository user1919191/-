package project.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.ThrowUtil;
import project.mapper.QuestionBankQuestionMapper;
import project.model.dto.questionBankQuestion.QuestionBankQuestionAddRequest;
import project.model.dto.questionBankQuestion.QuestionBankQuestionRemoveRequest;
import project.model.entity.QuestionBankQuestion;
import project.model.vo.QuestionVO;
import project.service.QuestionBankQuestionService;
import project.service.QuestionService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@RestController
@RequestMapping("/questionBankQuestion")
@Slf4j
public class QuestionBankQuestionController {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private UserService userService;
    @Autowired
    private QuestionBankQuestionMapper questionBankQuestionMapper;

    /**
     * 向题库中添加题目
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
    @SaCheckRole(UserConstant.Admin_Role)
    public BaseResponse<Boolean> removeQuestionBankQuestion(@RequestBody QuestionBankQuestionRemoveRequest removeRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(removeRequest == null || request == null, ErrorCode.PARAMS_ERROR,"参数为空");
        Long questionBankId = removeRequest.getQuestionBankId();
        Long questionId = removeRequest.getQuestionId();
        ThrowUtil.throwIf(questionBankId == null || questionId == null, ErrorCode.PARAMS_ERROR,"要删除的题目Id或题库Id为空");
        //2.数据校验
        LambdaQueryWrapper<QuestionBankQuestion> questionBankQuestionLambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId).eq(QuestionBankQuestion::getQuestionId, questionId);
        //3.执行删除操作
        boolean remove = questionBankQuestionService.remove(questionBankQuestionLambdaQueryWrapper);
        ThrowUtil.throwIf(!remove, ErrorCode.OPERATION_ERROR,"删除失败");
        return ResultUtil.success(true);
    }


}

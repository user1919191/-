package project.service.Imp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import project.common.ErrorCode;
import project.exception.ThrowUtil;
import project.mapper.QuestionBankQuestionMapper;
import project.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import project.model.entity.QuestionBankQuestion;
import project.model.entity.User;
import project.model.vo.QuestionBankQuestionVO;
import project.service.QuestionBankQuestionService;
import project.service.QuestionBankService;
import project.service.QuestionService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 *题库题目关联实现类
 */
public class QuestionBackQuestionServiceImp extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserService userService;

    /**
     * 校验题库题目关联
     * @param questionBankQuestion
     * @param add 对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        //1.参数校验
        ThrowUtil.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.通用数据校验
        //3.新增题目题库关联校验
    }

    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        return null;
    }

    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        return null;
    }

    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBank(List<Long> questionIdList, long questionBankId, User loginUser) {

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsFromBank(List<Long> questionIdList, long questionBankId) {

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {

    }
}

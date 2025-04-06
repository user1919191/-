package project.service.Imp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import project.manager.AiManager;
import project.mapper.QuestionMapper;
import project.model.dto.question.QuestionQueryRequest;
import project.model.entity.Question;
import project.model.entity.User;
import project.model.vo.QuestionVO;
import project.service.QuestionService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class QuestionServiceImp extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionBackService questionBackService;

    @Resource
    private AiManager aiManager;

    @Override
    public void validQuestion(Question question, boolean add) {

    }

    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        return null;
    }

    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        return null;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        return null;
    }

    @Override
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        return null;
    }

    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        return null;
    }

    @Override
    public void batchDeleteQuestions(List<Long> questionIdList) {

    }

    @Override
    public boolean aiGenerateQuestions(String questionType, int number, User user) {
        return false;
    }
}

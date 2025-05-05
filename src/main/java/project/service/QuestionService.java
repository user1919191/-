package project.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;
import project.model.dto.question.QuestionQueryRequest;
import project.model.entity.Question;
import project.model.entity.User;
import project.model.vo.QuestionVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 题目业务接口
 */

public interface QuestionService extends IService<Question> {

    /**
     * 校验数据
     * @param question
     * @param add
     */
    void validQuestion(Question question , boolean add);

    /**
     * 保存题目到数据库
     */
    boolean saveQuestion(Question question);

    /**
     * 根据ID删除题目
     * @param question
     * @return
     */
    boolean deleteQuestionById(Question question);

    /**
     * 更新题目
     * @param question
     * @return
     */
    boolean updateQuestion(Question question);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    /**
     * 根据题目ID获取题目封装
     * @param id
     * @return
     */
    Question getQuestionById(long id);
    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);

    /**
     * 分页获取题目列表
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest);

    /**
     * 从 ES 查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);

    /**
     * 批量删除题目
     *
     * @param questionIdList
     */

    @Transactional(rollbackFor = Exception.class)
    void batchDeleteQuestions(List<Long> questionIdList);

    /**
     * AI 生成题目
     * @param questionType 题目类型，比如 Java
     * @param number 题目数量，比如 10
     * @param user 创建人
     * @return ture / false
     */
    boolean aiGenerateQuestions(String questionType, int number, User user);

    /**
     * 从缓存中获取题目
     * @return
     */
    Page<QuestionVO> getQuestionFromCache(QuestionQueryRequest request);
}

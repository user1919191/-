package project.service.Imp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import project.common.ErrorCode;
import project.constant.CommonConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.mapper.QuestionBankQuestionMapper;
import project.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import project.model.entity.Question;
import project.model.entity.QuestionBank;
import project.model.entity.QuestionBankQuestion;
import project.model.entity.User;
import project.model.vo.QuestionBankQuestionVO;
import project.model.vo.UserVO;
import project.service.QuestionBankQuestionService;
import project.service.QuestionBankService;
import project.service.QuestionService;
import project.service.UserService;
import project.utils.SqlUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *题库题目关联实现类
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImp extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    @Lazy
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
        //2.获取校验数据
        Long questionBankQuestionId= questionBankQuestion.getId();
        Long questionId = questionBankQuestion.getQuestionId();
        Long questionBankId = questionBankQuestion.getQuestionBankId();
        Long questionUserId = questionBankQuestion.getUserId();
        Date questionCreateTime = questionBankQuestion.getCreateTime();
        Date questionUpdateTime = questionBankQuestion.getUpdateTime();
        Long changeUserId = questionBankQuestion.getChangeUserId();

        //3.通用数据校验
        ThrowUtil.throwIf(questionId == null || questionId <= 0,ErrorCode.PARAMS_ERROR,"题目id不能为空");
        ThrowUtil.throwIf(questionBankId == null || questionBankId <= 0,ErrorCode.PARAMS_ERROR,"题库id不能为空");
        ThrowUtil.throwIf(questionUserId == null || questionUserId <= 0,ErrorCode.PARAMS_ERROR,"创建用户id不能为空");
        ThrowUtil.throwIf(questionBankService.getById(questionBankId) == null, ErrorCode.NOT_FOUND, "题库不存在");
        ThrowUtil.throwIf(questionService.getById(questionId) == null,ErrorCode.PARAMS_ERROR,"题目不存在");
        // Todo 因为没有传入request参数 修改时间和修改用户单独校验
        //4.新增题目题库关联校验
        if(add){
            ThrowUtil.throwIf(questionCreateTime == null,ErrorCode.PARAMS_ERROR,"创建时间不能为空");
            ThrowUtil.throwIf(questionUpdateTime != null,ErrorCode.PARAMS_ERROR,"新增题目题库关联时更新时间不能存在");
            ThrowUtil.throwIf(changeUserId != null,ErrorCode.PARAMS_ERROR,"新增题目题库关联时更新用户id不能存在");
        }
    }

    /**
     * 获取题库题目关联查询条件
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        //1.参数校验
        if(questionBankQuestionQueryRequest == null){
            return queryWrapper;
        }
        //2.数据获取
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        //3.封装为查询条件并校验
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);

        queryWrapper.orderBy(SqlUtil.validSortField(sortField),sortOrder.equals(CommonConstant.Sort_Order_ASC),sortField);
        return queryWrapper;
    }

    /**
     * 获取题库题目关联封装
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(questionBankQuestion == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.获取封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);
        ThrowUtil.throwIf(questionBankQuestionVO == null, ErrorCode.PARAMS_ERROR,"获取题库题目关联失败");
        //3.封装其他数据
        Long questionUserId = questionBankQuestion.getUserId();
        if(questionUserId == null){
            log.warn("前端参数中获取题库题目关联用户Id失败,创建用户id为空,questionBankQuestion:{}",questionBankQuestion.getId());
            return questionBankQuestionVO;
        }
        User user = userService.getById(questionUserId);
        //即使没有查询到创建用户也可以成功返回,保证系统高可用
        if(user == null){
            log.error("获取题库题目关联用户失败,创建用户不存在,questionUserId:{}",questionUserId);
            return questionBankQuestionVO;
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);
        return questionBankQuestionVO;
    }

    /**
     * 分页获取题目封装
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(questionBankQuestionPage == null || request == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.1获取分页数据
        List<QuestionBankQuestion> questionRecords = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> qbqvoPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        //2.2获取分页封装
        if(CollectionUtils.isEmpty(questionRecords)){
            return qbqvoPage;
        }
        List<QuestionBankQuestionVO> questionBankQuestionVOS = questionRecords.stream().map(questionBankquestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankquestion);
        }).collect(Collectors.toList());

        //2.3封装其他数据
        List<Long> userIdCollect = questionRecords.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toList());
        Map<Long, List<User>> listMap = userService.listByIds(userIdCollect).stream().collect(Collectors.groupingBy(User::getId));
        //2.4填充用户数据
        questionBankQuestionVOS.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if(listMap .containsKey(userId)){
                user = listMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
        });
        qbqvoPage.setRecords(questionBankQuestionVOS);
        return qbqvoPage;
    }

    /**
     *批量添加题库
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    @Override
    public void batchAddQuestionsToBank(List<Long> questionIdList, long questionBankId, User loginUser) {
        //1.参数校验
        ThrowUtil.throwIf(CollectionUtils.isEmpty(questionIdList) ,ErrorCode.PARAMS_ERROR,"添加题目为空");
        ThrowUtil.throwIf(loginUser == null ,ErrorCode.PARAMS_ERROR,"用户不能为空");
        ThrowUtil.throwIf(questionBankId <= 0 ,ErrorCode.PARAMS_ERROR,"题库id不能小于等于0");
        //2.数据校验
        //2.1题目Id是否存在
        LambdaQueryWrapper<Question> questionWrapper = Wrappers.lambdaQuery(Question.class).select(Question::getId).in(Question::getId, questionIdList);
        //2.2题库是否存在
        QuestionBank questionBank= questionBankService.getById(questionBankId);
        ThrowUtil.throwIf(questionBank == null || questionBank.getId() <= 0,ErrorCode.PARAMS_ERROR,"题库不存在");
        //2.3获取合法题目Id列表
        List<Long> longList = questionService.listObjs(questionWrapper, obj -> (long) obj);
        ThrowUtil.throwIf(longList == null, ErrorCode.PARAMS_ERROR,"无合法题目,插入失败");
        //2.4查询哪些题目在题库中
        LambdaQueryWrapper<QuestionBankQuestion> questionNotInQBQ = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId).in(QuestionBankQuestion::getQuestionId, longList);
        List<QuestionBankQuestion> existQuestionList = this.list(questionNotInQBQ);
        Set<Long> existQuestionSet = existQuestionList.stream().map(QuestionBankQuestion::getQuestionId).collect(Collectors.toSet());
        //2.6筛选出不在题库中的题目ID
        List<Long> addQuestionList = questionIdList.stream().filter(question -> {
            return !existQuestionSet.contains(question);
        }).collect(Collectors.toList());
        //3.批量添加
        //3.1自定义线程池
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                20, 50, 30L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(10000)
                ,new ThreadPoolExecutor.CallerRunsPolicy());
        //3.2保存所有批次
        ArrayList<CompletableFuture<Void>> futureList = new ArrayList<>();
        int batchSize = 1000;
        //3.3分批处理,避免长事务
        int totalQuestionListSize = longList.size();
        for(int i= 0;i < totalQuestionListSize;i+=batchSize){
            //生成批次书数据
            List<Long> subList = longList.subList(i, Math.min(totalQuestionListSize, i + batchSize));
            List<QuestionBankQuestion> questionBankQuestions = subList.stream().map(question -> {
                QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                questionBankQuestion.setQuestionBankId(questionBankId);
                questionBankQuestion.setQuestionId(question);
                questionBankQuestion.setUserId(loginUser.getId());
                return questionBankQuestion;
            }).collect(Collectors.toList());
            //3.4获取代理对象
            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImp)AopContext.currentProxy();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestions);
            }, threadPool);
            //3.5将任务添加到异步列表
            futureList.add(future);
        }
        //3.6等待所有任务完成
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();
        //3.7关闭线程池
        threadPool.shutdown();
    }

    /**
     * 批量删除题目题库关联
     * @param questionIdList
     * @param questionBankId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsFromBank(List<Long> questionIdList, long questionBankId) {
        //1.参数校验
        ThrowUtil.throwIf(CollectionUtils.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目id不能为空");
        ThrowUtil.throwIf(questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库id不能小于等于0");
        //2.数据校验
        //2.1题库是否存在
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtil.throwIf(questionBank == null || questionBank.getId() <= 0, ErrorCode.PARAMS_ERROR, "题库不存在");
        //2.2题目是否存在
        List<Question> questions = questionService.listByIds(questionIdList);
        Set<Long> existIds = questions.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());
        List<Long> existQuestionList = questionIdList.stream().filter(existIds::contains).collect(Collectors.toList());
        ThrowUtil.throwIf(existQuestionList.isEmpty(), ErrorCode.PARAMS_ERROR, "题目不存在");
        //3.批量删除
        for(Long questionId : existQuestionList){
            LambdaQueryWrapper<QuestionBankQuestion> questionBankQuestionLambdaQueryWrapper = Wrappers
                    .lambdaQuery(QuestionBankQuestion.class).eq(QuestionBankQuestion::getQuestionId, questionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            boolean result = this.remove(questionBankQuestionLambdaQueryWrapper);
            if(!result){
                log.error("从题库ID:{}删除题目ID:{} 失败",questionBankId,questionId);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "从题库删除题目失败");
            }
        }
    }

    /**
     * 批量添加题目到题库(仅供内部调用)
     * @param questionBankQuestions
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
        try {
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突或违反其他完整性约束, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库连接问题、事务问题等导致操作失败, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库时发生未知错误，错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }
    }
}

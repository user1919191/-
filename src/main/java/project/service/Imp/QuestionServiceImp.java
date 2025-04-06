package project.service.Imp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import project.annotation.RateLimiter;
import project.common.ErrorCode;
import project.constant.CommonConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.manager.AiManager;
import project.mapper.QuestionMapper;
import project.model.dto.question.QuestionEsDTO;
import project.model.dto.question.QuestionQueryRequest;
import project.model.entity.Question;
import project.model.entity.QuestionBankQuestion;
import project.model.entity.User;
import project.model.vo.QuestionVO;
import project.model.vo.UserVO;
import project.service.QuestionService;
import project.service.UserService;
import project.utils.SqlUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuestionServiceImp extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private UserService userService;

    @Resource
    private QuestionBackQuestionService questionBackQuestionService;

    @Resource
    private AiManager aiManager;

    /**
     * 校验问题信息
     * @param question
     * @param add
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        //1.参数校验
        ThrowUtil.throwIf(question == null || !add, ErrorCode.PARAMS_ERROR);
        //2.问题是否合法
        ThrowUtil.throwIf(question.getId() < 0, ErrorCode.PARAMS_ERROR, "问题ID不能为负数");
        ThrowUtil.throwIf(StringUtils.isBlank(question.getTitle()) || question.getTitle().length() < 2 || question.getTitle().length() > 80,
                ErrorCode.PARAMS_ERROR, "标题长度存在问题");
        ThrowUtil.throwIf(StringUtils.isBlank(question.getContent()) || question.getContent().length() < 10 || question.getContent().length() > 10000,
                ErrorCode.PARAMS_ERROR, "内容长度存在问题");
        ThrowUtil.throwIf(question.getUserId() == null, ErrorCode.PARAMS_ERROR, "创建用户ID不能为空");
    }

    /**
     * 获取查询条件
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        //1.参数校验
        ThrowUtil.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //2.构建查询条件
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        List<String> tagList = questionQueryRequest.getTags();
        Long userId = questionQueryRequest.getUserId();
        String answer = questionQueryRequest.getAnswer();

        if(StringUtils.isNotBlank(searchText)){
            queryWrapper.like("title", searchText).or().like("content", searchText);
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);

        if(CollUtil.isNotEmpty(tagList)){
            for(String tag : tagList){
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);

        queryWrapper.orderBy(SqlUtil.validSortField(sortField),
                sortOrder.equals(CommonConstant.Sort_Order_ASC),
                sortField);

        return queryWrapper;
    }

    /**
     * 获取问题封装
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(question == null || request == null || question.getIsDelete().equals(0),
                ErrorCode.PARAMS_ERROR,"问题不存在");
        //2.转化为封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);
        ThrowUtil.throwIf(!questionVO.getId().equals(question.getId()), ErrorCode.PARAMS_ERROR,"问题ID不匹配");
        if(questionVO.getId() == null || questionVO.getId() < 0){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "问题ID不能为空");
        }
        User user = userService.getById(questionVO.getId());
        ThrowUtil.throwIf(user == null, ErrorCode.OPERATION_ERROR, "用户不存在");
        UserVO userVO = userService.getUserVO(user);
        ThrowUtil.throwIf(userVO == null, ErrorCode.OPERATION_ERROR, "用户不存在");
        questionVO.setUser(userVO);
        return questionVO;
    }

    /**
     * 分页获取题目封装
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(questionPage == null || request == null, ErrorCode.PARAMS_ERROR);
        //2.获取题目封装
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> QuestionVoPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if(CollUtil.isEmpty(questionList)){
            return QuestionVoPage;
        }
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            return QuestionVO.objToVo(question);
        }).collect(Collectors.toList());

        List<Long> collect = questionList.stream().map(Question::getUserId).collect(Collectors.toList());
        Map<Long, List<User>> UserIdListMap = userService.listByIds(collect).stream().collect(Collectors.groupingBy(User::getId));
        questionVOList.forEach(questionVo->{
            Long userId = questionVo.getUserId();
            User user = null;
            if(UserIdListMap.containsKey(userId)){
                user = UserIdListMap.get(userId).get(0);
            }
            questionVo.setUser(userService.getUserVO(user));
        });
        QuestionVoPage.setRecords(questionVOList);
        return QuestionVoPage;
    }

    /**
     * 分页获取题目列表
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        int page = questionQueryRequest.getPage();
        int size = questionQueryRequest.getSize();
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        ThrowUtil.throwIf(questionBankId == null, ErrorCode.PARAMS_ERROR, "题库ID不能为空");
        LambdaQueryWrapper<QuestionBankQuestion> questionBankQuestionLambdaQueryWrapper = Wrappers
                .lambdaQuery(QuestionBankQuestion.class).select(QuestionBankQuestion::getQuestionId)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
        List<Question> questionList = questionBackService.list(questionBankQuestionLambdaQueryWrapper);
        if(CollUtil.isNotEmpty(questionList)){
            List<Long> questionIdList = questionList.stream().map(Question::getId).collect(Collectors.toList());
            queryWrapper.in("id", questionIdList);
        }else{
            return new Page<>(page, size, 0);
        }
        Page<Question> questionPage = this.page(new Page<>(page, size), queryWrapper);
        return questionPage;
    }

    /**
     * 从ES中获取题目
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        List<String> tags = questionQueryRequest.getTags();
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        Long userId = questionQueryRequest.getUserId();
        int current = questionQueryRequest.getPage() - 1;
        int pageSize = questionQueryRequest.getSize();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        if (notId != null) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id", notId));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        if (questionBankId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("questionBankId", questionBankId));
        }

        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }

        if (StringUtils.isNotBlank(searchText)) {
            // title = '' or content = '' or answer = ''
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
            boolQueryBuilder.minimumShouldMatch(1);
        }

        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StringUtils.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.Sort_Order_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }

        PageRequest pageRequest = PageRequest.of(current, pageSize);

        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(pageRequest)
                .withSorts(sortBuilder)
                .build();
        SearchHits<QuestionEsDTO> searchHits = elasticsearchRestTemplate.search(searchQuery, QuestionEsDTO.class);

        Page<Question> page = new Page<>();
        page.setTotal(searchHits.getTotalHits());
        List<Question> resourceList = new ArrayList<>();
        if (searchHits.hasSearchHits()) {
            List<SearchHit<QuestionEsDTO>> searchHitList = searchHits.getSearchHits();
            for (SearchHit<QuestionEsDTO> questionEsDTOSearchHit : searchHitList) {
                resourceList.add(QuestionEsDTO.dtoToObj(questionEsDTOSearchHit.getContent()));
            }
        }
        page.setRecords(resourceList);
        return page;
    }

    @Override
    public void batchDeleteQuestions(List<Long> questionIdList) {
        ThrowUtil.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "要删除的题目列表不能为空");
        for (Long questionId : questionIdList) {
            boolean result = this.removeById(questionId);
            ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目失败");

            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId);
            result = questionBackQuestionService.remove(lambdaQueryWrapper);

            ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目题库关联失败");
        }
    }

    @Override
    public boolean aiGenerateQuestions(String questionType, int number, User user) {
        //1.参数校验
        if (ObjectUtil.hasEmpty(questionType, number, user)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        String systemPrompt = "你是一位专业的程序员面试官，你要帮我生成 {数量} 道 {方向} 面试题，要求输出格式如下：\n" +
                "\n" + "1. 什么是 Java 中的反射？\n" + "2. Java 8 中的 Stream API 有什么作用？\n" +
                "3. xxxxxx\n" + "\n" + "除此之外，请不要输出任何多余的内容，不要输出开头、也不要输出结尾，只输出上面的列表。\n" +
                "除此之外，请不要输出任何多余的内容，不要输出开头、也不要输出结尾，只输出上面的列表。\n" + "\n" +
                "接下来我会给你要生成的题目{数量}、以及题目{方向}\n";
        // 2. 拼接用户 Prompt
        String userPrompt = String.format("题目数量：%s, 题目方向：%s", number, questionType);
        // 3. 调用 AI 生成题目
        String answer = aiManager.doChat(systemPrompt, userPrompt);
        // 4. 对题目进行预处理
        // 按行拆分
        List<String> lines = Arrays.asList(answer.split("\n"));
        // 移除序号和 `
        List<String> titleList = lines.stream()
                .map(line -> StrUtil.removePrefix(line, StrUtil.subBefore(line, " ", false))) // 移除序号
                .map(line -> line.replace("`", "")) // 移除 `
                .collect(Collectors.toList());
        // 5. 保存题目到数据库中
        List<Question> questionList = titleList.stream().map(title -> {
            Question question = new Question();
            question.setTitle(title);
            question.setUserId(user.getId());
            question.setTags("[\"待审核\"]");
            // 优化点：可以并发生成
            question.setAnswer(aiGenerateQuestionAnswer(title));
            return question;
        }).collect(Collectors.toList());
        boolean result = this.saveBatch(questionList);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存题目失败");
        }
        return true;
    }

    /**
     * AI生成题解
     * @param questionTitle
     * @return
     */
    private String aiGenerateQuestionAnswer(String questionTitle) {
        String questionPrompt = "你是一位专业的程序员面试官，我会给你一道面试题，请帮我生成详细的题解。要求如下：\n" +
                "\n" + "1. 题解的语句要自然流畅\n" + "2. 题解可以先给出总结性的回答，再详细解释\n" +
                "3. 要使用 Markdown 语法输出\n" + "\n" +
                "除此之外，请不要输出任何多余的内容，不要输出开头、也不要输出结尾，只输出题解。\n" + "\n" +
                "接下来我会给你要生成的面试题";

        String userPrompt = String.format("面试题：%s", questionTitle);
        return aiManager.doChat(userPrompt, questionPrompt);
    }
}

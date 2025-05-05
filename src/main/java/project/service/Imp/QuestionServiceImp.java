package project.service.Imp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import project.service.QuestionBankQuestionService;
import project.service.QuestionService;
import project.service.UserService;
import project.utils.SqlUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 问题实现类
 */

@Service
public class QuestionServiceImp extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    /**
     * 通用前缀
     */
    private final String QUESTION_PREFIX = "question:";

    /**
     * 本地Caffeine缓存
     */
    @Resource(name ="questionCache")
    private Cache<String, Object> questionVOCache;

    /**
     * 事务式编程
     */
    private TransactionTemplate transactionTemplate;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private QuestionBankQuestionService questionBackQuestionService;

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
        ThrowUtil.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        //2.获取数据
        Long questionId = question.getId();
        String questionTitle = question.getTitle();
        String questionContent = question.getContent();
        String questionTags = question.getTags();
        Date questionCreateTime = question.getCreateTime();
        Integer questionIsDelete = question.getIsDelete();
        Long questionUserId = question.getUserId();
        String questionAnswer = question.getAnswer();
        Date questionEditTime = question.getEditTime();
        Date questionUpdateTime = question.getUpdateTime();
        Long questionCloseChangeId = question.getCloseChangeId();
        //3.通用校验
        ThrowUtil.throwIf(StringUtils.isBlank(questionTitle) || questionTitle.length() < 2 || questionTitle.length() > 80,
                ErrorCode.PARAMS_ERROR, "标题长度存在问题");
        ThrowUtil.throwIf(StringUtils.isBlank(questionContent) || questionContent.length() < 10 || questionContent.length() > 10000,
                ErrorCode.PARAMS_ERROR, "内容长度存在问题");
        ThrowUtil.throwIf(StringUtils.isBlank(questionTags), ErrorCode.PARAMS_ERROR, "标签不能为空");
        ThrowUtil.throwIf(questionCreateTime == null, ErrorCode.PARAMS_ERROR, "创建时间不能为空");
        ThrowUtil.throwIf(questionIsDelete == null || !(questionIsDelete == 0 || questionIsDelete == 1), ErrorCode.PARAMS_ERROR, "删除状态非法");
        ThrowUtil.throwIf(questionUserId == null, ErrorCode.PARAMS_ERROR, "创建用户ID不能为空");
        //4.对于新增题目补充校验
        if (add) {
            ThrowUtil.throwIf(StringUtils.isNotBlank(questionAnswer), ErrorCode.PARAMS_ERROR, "不应该有推荐答案,因为还没增加");
            ThrowUtil.throwIf(questionEditTime != null, ErrorCode.PARAMS_ERROR, "不应该有编辑时间,因为还没增加");
            ThrowUtil.throwIf(questionUpdateTime != null, ErrorCode.PARAMS_ERROR, "不应该有更新时间,因为还没增加");
            ThrowUtil.throwIf(questionCloseChangeId != null, ErrorCode.PARAMS_ERROR, "不应该有关闭变更ID,因为还没增加");
        }else{
            //Todo 踩坑笔记:Question的主键为雪花算法生成,只有当插入数据库时才自动生成(如果未指定),所以在插入前不能判断Id为null,否则会报错
            ThrowUtil.throwIf(questionId == null || (questionId <= 0), ErrorCode.PARAMS_ERROR, "问题ID不能为负数或空");
        }
    }

    /**
     * 保存题目
     * @param question
     * @return
     */
    @Override
    public boolean saveQuestion(Question question) {
        //1.保存到数据库
        boolean save = this.save(question);
        //2.异步保存到ES
        //Todo 使用Canal还是MQ?
        return true;
    }

    /**
     * 根据ID删除题目
     * @param quesiton
     * @return
     */
    //Todo 优化事务粒度
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteQuestionById(Question quesiton) {
        //1.参数校验
        Long questionId = quesiton.getId();
        String cacheQuestionId = QUESTION_PREFIX + questionId;
        ThrowUtil.throwIf(questionId == null, ErrorCode.PARAMS_ERROR
                ,"增加题目错误,请重试");
        //2.删除数据库
        //3.更新HotKey
        //Todo 将值设置为null,避免热Key高并发造成数据库崩溃
        //4.更新Caffeine
        // 5.返回结果
            //2.删除数据库
            boolean saved = updateById(quesiton);
            //3.更新HotKey
            if (JdHotKeyStore.isHotKey(cacheQuestionId)) {
                JdHotKeyStore.remove(cacheQuestionId);
                //Todo 将值设置为null,避免热Key高并发造成数据库崩溃
                questionVOCache.put(questionId.toString(), null);
            }
            //4.更新Caffeine
            questionVOCache.invalidate(questionId);
            // 5.返回结果
            return saved;
    }

    /**
     * 根据ID更新题目
     * @param question
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateQuestion(Question question) {
            //1.更新数据库
            boolean updated = updateById(question);
            //2.更新HotKey
            Long questionId = question.getId();
            String cacheQustion = QUESTION_PREFIX + questionId;
            if(JdHotKeyStore.isHotKey(cacheQustion)){
                JdHotKeyStore.remove(cacheQustion);
            }
            //3.更新Caffeine
            questionVOCache.put(questionId.toString(),question);
            //4.返回结果
            return updated;
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
        //Todo 补充查询条件
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
     * 根据ID获取问题(优先缓存)
     * @param id
     * @return
     */
    @Override
    public Question getQuestionById(long id) {
        //1.从Caffeine从获取
        Object cacheQuestion = questionVOCache.getIfPresent(id);
        if(cacheQuestion!= null){
            return (Question) cacheQuestion;
        }
        //2.从HotKey中获取
        String hotKeyQuestion = QUESTION_PREFIX + id;
        if(JdHotKeyStore.isHotKey(hotKeyQuestion)){
            Question question = (Question) JdHotKeyStore.get(hotKeyQuestion);
            JdHotKeyStore.smartSet(hotKeyQuestion, question);
            return question;
        }
        //3.如果不存在,从数据库查询
        Question question = getById(id);
        ThrowUtil.throwIf(question == null, ErrorCode.PARAMS_ERROR, "问题不存在");
        //4.存入缓存
        //Todo 有无优化必要(异步)
        questionVOCache.put(Long.toString(id), question);
        return question;
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
        ThrowUtil.throwIf(question == null || request == null || question.getIsDelete().equals(1),
                ErrorCode.PARAMS_ERROR,"问题不存在");
        //2.转化为封装类
        Long id = question.getId();
        //3.先从缓存中获取
        String questionKey = QUESTION_PREFIX + id;
        QuestionVO cacheQuestionVO = (QuestionVO)questionVOCache.getIfPresent(questionKey);
        if(ObjectUtils.isNotEmpty(cacheQuestionVO)){
           return cacheQuestionVO;
       }
        //3.2从HotKey中获取
        if(JdHotKeyStore.isHotKey(questionKey)){
            Question questionCache = (Question) JdHotKeyStore.get(questionKey);
            return QuestionVO.objToVo(questionCache);
        }
        //4.转为封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);
        ThrowUtil.throwIf(!questionVO.getId().equals(id), ErrorCode.PARAMS_ERROR,"问题ID不匹配");
        if(questionVO.getId() == null || questionVO.getId() < 0){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "问题ID不能为空");
        }
        User user = userService.getById(questionVO.getId());
        if(user == null){
            questionVO.setUser(null);
        }else{
            UserVO userVO = userService.getUserVO(user);
            questionVO.setUser(userVO);
        }
        //存入缓存
        questionVOCache.put(id.toString(), questionVO);
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
        List<QuestionBankQuestion> questionList = questionBackQuestionService.list(questionBankQuestionLambdaQueryWrapper);
        if(CollUtil.isNotEmpty(questionList)){
            List<Long> questionIdList = questionList.stream().map(QuestionBankQuestion::getId).collect(Collectors.toList());
            queryWrapper.in("id", questionIdList);
        }else{
            return new Page<>(page, size, 0);
        }
        Page<Question> questionPage = this.page(new Page<>(page, size), queryWrapper);
        return questionPage;
    }

    /**
     * 降级策略从缓存遍历返回
     */
    @Override
    public Page<QuestionVO> getQuestionFromCache(QuestionQueryRequest request){
        //1.获取分页参数
        int page = request.getPage();
        int size = request.getSize();
        Page<QuestionVO> questionVOPage = new Page<>(page, size);
        //2.获取缓存中的数据
        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, QuestionVO> questionVOCacheMap =
                (ConcurrentMap<Long, QuestionVO>) (ConcurrentMap<?, ?>) questionVOCache.asMap();
        if(CollUtil.isEmpty(questionVOCacheMap)){
            return new Page<>(1, 10, 0);
        }
        List<QuestionVO> questionVOList = new ArrayList<>(questionVOCacheMap.values());
        questionVOPage.setRecords(questionVOList);
        return  questionVOPage;
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

    /**
     *批量删除操作
     * @param questionIdList
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteQuestions(List<Long> questionIdList) {
        ThrowUtil.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "要删除的题目列表不能为空");
        List<String> cacheDeleteList = new ArrayList<>();
        for (Long questionId : questionIdList) {
            //逻辑删除题目
            boolean result = this.removeById(questionId);
            ThrowUtil.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目失败");
            //批量从缓存中删除
            questionVOCache.invalidateAll(cacheDeleteList);
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId);
            questionBackQuestionService.remove(lambdaQueryWrapper);
        }
    }
    /**
     * AI生成题目
     * @param questionType
     * @param number
     * @param user
     * @return
     */
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
        // 4. 解析 AI 生成的题目
        List<String> questionList = Arrays.asList(answer.split("\n"));
        //5.规范化格式
        List<String> questionCollect = questionList.stream().map(line -> line.substring(line.indexOf(" ") + 1))
                .map(line -> line.replace("`", "")).collect(Collectors.toList());
        //6.保存题目到数据库中(使用线程池提高性能)
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(questionCollect.size(), 10));
        List<Question> questions = questionCollect.stream().map(title -> CompletableFuture.supplyAsync(() -> {
            Question question = new Question();
            question.setTitle(title);
            question.setUserId(user.getId());
            question.setCreateTime(new Date());
            question.setTags("[\"待审核\"]");
            question.setContent(aiGenerateQuestionAnswer(title));
            return question;
        }, pool)).collect(Collectors.toList()).stream().map(CompletableFuture::join).collect(Collectors.toList());
        //批量保存题目
        boolean saveBatch = this.saveBatch(questions);
        ThrowUtil.throwIf(!saveBatch, ErrorCode.OPERATION_ERROR, "保存题目失败");
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

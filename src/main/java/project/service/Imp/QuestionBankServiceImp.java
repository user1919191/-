package project.service.Imp;


import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.constant.CommonConstant;
import project.exception.ThrowUtil;
import project.mapper.QuestionBankMapper;
import project.model.dto.questionBank.QuestionBankQueryRequest;
import project.model.entity.QuestionBank;
import project.model.entity.User;
import project.model.vo.QuestionBankVO;
import project.model.vo.QuestionVO;
import project.model.vo.UserVO;
import project.service.QuestionBankService;
import project.service.UserService;
import project.utils.SqlUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuestionBankServiceImp extends ServiceImpl<QuestionBankMapper, QuestionBank> implements QuestionBankService {

    @Resource(name = "userCache")
    private Cache<String, Object> userCache;

    @Resource
    private UserService userService;

    /**
     * 校验数据合法性
     * @param questionBank
     * @param add 对创建的数据进行校验
     */
    @Override
    public void validQuestionBank( QuestionBank questionBank, boolean add) {
        //1.参数校验
        ThrowUtil.throwIf(questionBank == null , ErrorCode.PARAMS_ERROR, "参数错误");
        //2.数据校验
        Long bankId = questionBank.getId();
        String bankDescription = questionBank.getDescription();
        String bankTitle = questionBank.getTitle();
        String bankPicture = questionBank.getPicture();
        Long bankUserId = questionBank.getUserId();
        Date bankCreateTime = questionBank.getCreateTime();
        Date bankEditTime = questionBank.getEditTime();
        Date bankUpdateTime = questionBank.getUpdateTime();
        Integer bankIsDelete = questionBank.getIsDelete();
        ThrowUtil.throwIf(StringUtils.isEmpty(bankTitle) || bankTitle.length() <= 1 || bankTitle.length() >= 50
                , ErrorCode.PARAMS_ERROR, "题库名称错误");
        ThrowUtil.throwIf(StringUtils.isEmpty(bankDescription) || bankDescription.length() <= 1
                || bankDescription.length() >= 100, ErrorCode.PARAMS_ERROR, "题库描述错误");
        ThrowUtil.throwIf(StringUtils.isEmpty(bankPicture) || bankPicture.length() <= 1
                , ErrorCode.PARAMS_ERROR, "题库图片错误");
        ThrowUtil.throwIf(bankUserId == null || bankUserId <= 0, ErrorCode.PARAMS_ERROR, "题库用户id错误");
        ThrowUtil.throwIf(bankCreateTime == null, ErrorCode.PARAMS_ERROR, "题库创建时间错误");
        //3.新增数据补充校验
        if (add) {
            ThrowUtil.throwIf(bankEditTime != null, ErrorCode.PARAMS_ERROR, "题库编辑时间错误");
            ThrowUtil.throwIf(bankUpdateTime != null, ErrorCode.PARAMS_ERROR, "题库更新时间错误");
        }
    }

    /**
     * 获取查询条件
     * @param questionBankQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest) {
        //1.参数校验
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        if(questionBankQueryRequest == null) {
            return queryWrapper;
        }
        //2.构造查询条件
        Long bankId = questionBankQueryRequest.getId();
        String bankDescription = questionBankQueryRequest.getDescription();
        String bankTitle = questionBankQueryRequest.getTitle();
        String bankPicture = questionBankQueryRequest.getPicture();
        Long bankUserId = questionBankQueryRequest.getUserId();
        Long bankNotId = questionBankQueryRequest.getNotId();
        String bankSearchTest = questionBankQueryRequest.getSearchText();
        String sortField = questionBankQueryRequest.getSortField();
        String sortOrder = questionBankQueryRequest.getSortOrder();

        if(StringUtils.isNotEmpty(bankSearchTest)){
            queryWrapper.and(qw -> qw.like("title", bankSearchTest).or().like("description", bankSearchTest));
        }
        queryWrapper.like(StringUtils.isNotEmpty(bankTitle), "title", bankTitle)
                .like(StringUtils.isNotEmpty(bankDescription), "description", bankDescription);
        queryWrapper.ne(ObjectUtils.isNotEmpty(bankNotId),"id", bankNotId)
                .eq(ObjectUtils.isNotEmpty(bankId),"id",bankId)
                .eq(ObjectUtils.isNotEmpty(bankUserId), "user_id", bankUserId)
                .eq(ObjectUtils.isNotEmpty(bankPicture), "picture", bankPicture);
        queryWrapper.orderBy(SqlUtil.validSortField(sortField), sortOrder.equals(CommonConstant.Sort_Order_ASC), sortField);
        return queryWrapper;
    }

    /**
     * 获取题库封装
     * @param questionBank
     * @param request
     * @return
     */
    @Override
    public QuestionBankVO getQuestionBankVO(QuestionBank questionBank, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(questionBank == null, ErrorCode.PARAMS_ERROR, "参数错误");
        //2.转化为封装类
        QuestionBankVO questionBankVO = QuestionBankVO.objToVo(questionBank);

        //2.1封装User信息
        Long userId = questionBank.getUserId();
        ThrowUtil.throwIf( userId == null || userId <= 0,ErrorCode.PARAMS_ERROR, "用户id错误");
        User user = userService.getById(userId);
        ThrowUtil.throwIf(user == null, ErrorCode.PARAMS_ERROR, "用户不存在");
        questionBankVO.setUser(userService.getUserVO(user));

        return questionBankVO;
    }

    /**
     * 分页获取题库封装
     * @param questionBankPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> questionBankPage, HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(questionBankPage == null, ErrorCode.PARAMS_ERROR, "参数错误");
        //2.解析参数
        List<QuestionBank> questionBankPageRecords = questionBankPage.getRecords();
        Page<QuestionBankVO> page = new Page<>(questionBankPage.getCurrent(), questionBankPage.getSize(), questionBankPage.getTotal());
        if(CollUtil.isEmpty(questionBankPageRecords)) {
            return page;
        }

        //3.1补充用户信息
        Set<Long> userCollect = questionBankPageRecords.stream().map(QuestionBank::getUserId).collect(Collectors.toSet());
        //3.2转化为封装
        List<QuestionBankVO> questionBankVOS = questionBankPageRecords.stream().map(QuestionBankVO::objToVo)
                .collect(Collectors.toList());
        //3.3从缓存批量获取用户信息
        Map<Long, UserVO> userVOMap = batchGetUserVOs(userCollect);
        questionBankVOS.forEach(questionBankVO -> questionBankVO.setUser(userVOMap.get(questionBankVO.getUserId())));

        return page.setRecords(questionBankVOS);
    }

    /**
     * 从缓存批量获取用户信息
     */
    private Map<Long,UserVO> batchGetUserVOs(Set<Long> userIds) {
        if(CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }

        HashMap<Long,UserVO> userIdMap = new HashMap<>(userIds.size());
        HashSet<Long> sqlSearchSet = new HashSet<>();
        //1.查询缓存是否存在,不存在去数据库查询
        @SuppressWarnings("unchecked")
        ConcurrentMap<Long, UserVO> userVOCacheMap =
                (ConcurrentMap<Long, UserVO>) (ConcurrentMap<?, ?>) userCache.asMap();
        for(Long userId : userIds) {
            UserVO userVO = userVOCacheMap.get(userId);
            if(userVO != null) {
                userIdMap.put(userId, userVO);
            }else{
                sqlSearchSet.add(userId);
            }
        }
        //2.将查询数据添加到缓存
        if(CollUtil.isEmpty(sqlSearchSet)) {
            return userIdMap;
        }
        List<User> userList = userService.listByIds(sqlSearchSet);
        if(userList == null) {
            log.error("从数据库查询用户信息失败");
            return userIdMap;
        }
        //Todo 可以优化为线程池异步+Cache.putAll(线程安全)
        userList.stream().map(userService::getUserVO).forEach(user ->{
            userIdMap.put(user.getId(), user);
            userVOCacheMap.put(user.getId(), user);
        });
        //3.返回
        return userIdMap;
    }
}

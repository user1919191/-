package project.service.Imp;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.constant.CommonConstant;
import project.exception.ThrowUtil;
import project.mapper.PostFavourMapper;
import project.mapper.PostMapper;
import project.mapper.PostThumbMapper;
import project.model.dto.post.PostQueryRequest;
import project.model.entity.Post;
import project.model.entity.PostFavour;
import project.model.entity.PostThumb;
import project.model.entity.User;
import project.model.vo.PostVO;
import project.model.vo.UserVO;
import project.service.PostService;
import project.service.UserService;
import project.utils.SqlUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostServiceImp extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Resource
    private PostThumbMapper postThumbMapper;

    @Resource
    private PostFavourMapper postFavourMapper;

    @Override
    public void validPost(Post post, boolean add) {
        ThrowUtil.throwIf(post == null, ErrorCode.PARAMS_ERROR, "帖子不能为空");
        String title = post.getTitle();
        String content = post.getContent();
        String tags = post.getTags();
        Integer thumbNum = post.getThumbNum();
        Integer favourNum = post.getFavourNum();
        Long userId = post.getUserId();
        ThrowUtil.throwIf(ObjectUtils.isEmpty(title) || title.length() > 50, ErrorCode.PARAMS_ERROR, "标题不合法");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(content) || content.length() > 10000, ErrorCode.PARAMS_ERROR, "内容不合法");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(tags) || tags.length() > 100, ErrorCode.PARAMS_ERROR, "标签不合法");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(userId) || userId < 0, ErrorCode.PARAMS_ERROR, "用户ID不合法");
        if (add) {
            ThrowUtil.throwIf(thumbNum != null && thumbNum != 0, ErrorCode.PARAMS_ERROR, "点赞数不合法");
            ThrowUtil.throwIf(favourNum != null && favourNum != 0, ErrorCode.PARAMS_ERROR, "收藏数不合法");
        }
    }

    @Override
    public QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        if (postQueryRequest == null) {
            return queryWrapper;
        }
        String searchText = postQueryRequest.getSearchText();
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();
        Long id = postQueryRequest.getId();
        String title = postQueryRequest.getTitle();
        String content = postQueryRequest.getContent();
        List<String> tagList = postQueryRequest.getTags();
        Long userId = postQueryRequest.getUserId();
        Long notId = postQueryRequest.getNotId();
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtil.validSortField(sortField), CommonConstant.Sort_Order_ASC.equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public Page<Post> searchFromEs(PostQueryRequest postQueryRequest) {
        QueryWrapper<Post> queryWrapper = getQueryWrapper(postQueryRequest);
        List<String> orTagList = postQueryRequest.getOrTags();
        if (CollUtil.isNotEmpty(orTagList)) {
            queryWrapper.and(wrapper -> {
                boolean first = true;
                for (String tag : orTagList) {
                    if (StringUtils.isBlank(tag)) {
                        continue;
                    }
                    if (first) {
                        wrapper.like("tags", "\"" + tag + "\"");
                        first = false;
                    } else {
                        wrapper.or().like("tags", "\"" + tag + "\"");
                    }
                }
            });
        }
        return this.page(new Page<>(postQueryRequest.getPage(), postQueryRequest.getSize()), queryWrapper);
    }

    @Override
    public PostVO getPostVO(Post post, HttpServletRequest request) {
        PostVO postVO = PostVO.objToVo(post);
        long postId = post.getId();
        Long userId = post.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        postVO.setUser(userVO);
        User loginUser = userService.getLoginUser(request);
        if (loginUser != null) {
            QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
            postThumbQueryWrapper.in("postId", postId);
            postThumbQueryWrapper.eq("userId", loginUser.getId());
            PostThumb postThumb = postThumbMapper.selectOne(postThumbQueryWrapper);
            postVO.setHasThumb(postThumb != null);

            QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
            postFavourQueryWrapper.in("postId", postId);
            postFavourQueryWrapper.eq("userId", loginUser.getId());
            PostFavour postFavour = postFavourMapper.selectOne(postFavourQueryWrapper);
            postVO.setHasFavour(postFavour != null);
        }
        return postVO;
    }

    @Override
    public Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request) {
        List<Post> postList = postPage.getRecords();
        Page<PostVO> postVOPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        if (CollUtil.isEmpty(postList)) {
            return postVOPage;
        }
        Set<Long> userIdSet = postList.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, Boolean> postIdHasThumbMap = new HashMap<>();
        Map<Long, Boolean> postIdHasFavourMap = new HashMap<>();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            Set<Long> postIdSet = postList.stream().map(Post::getId).collect(Collectors.toSet());
            QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
            postThumbQueryWrapper.in("postId", postIdSet);
            postThumbQueryWrapper.eq("userId", loginUser.getId());
            List<PostThumb> postPostThumbList = postThumbMapper.selectList(postThumbQueryWrapper);
            postPostThumbList.forEach(postPostThumb -> postIdHasThumbMap.put(postPostThumb.getPostId(), true));

            QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
            postFavourQueryWrapper.in("postId", postIdSet);
            postFavourQueryWrapper.eq("userId", loginUser.getId());
            List<PostFavour> postFavourList = postFavourMapper.selectList(postFavourQueryWrapper);
            postFavourList.forEach(postFavour -> postIdHasFavourMap.put(postFavour.getPostId(), true));
        }
        List<PostVO> postVOList = postList.stream().map(post -> {
            PostVO postVO = PostVO.objToVo(post);
            Long userId = post.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            postVO.setUser(userService.getUserVO(user));
            postVO.setHasThumb(postIdHasThumbMap.getOrDefault(post.getId(), false));
            postVO.setHasFavour(postIdHasFavourMap.getOrDefault(post.getId(), false));
            return postVO;
        }).collect(Collectors.toList());
        postVOPage.setRecords(postVOList);
        return postVOPage;
    }
}

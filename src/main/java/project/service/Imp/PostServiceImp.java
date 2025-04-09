package project.service.Imp;


import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseBody;
import project.common.ErrorCode;
import project.exception.ThrowUtil;
import project.mapper.PostFavourMapper;
import project.mapper.PostMapper;
import project.mapper.PostThumbMapper;
import project.model.dto.post.PostQueryRequest;
import project.model.entity.Post;
import project.model.vo.PostVO;
import project.service.PostService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 帖子业务
 */
@Service
@Slf4j
public class PostServiceImp extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Resource
    private PostThumbMapper postThumbMapper;

    @Resource
    private PostFavourMapper postFavourMapper;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;
    /**
     * 校验
     * @param post
     * @param add
     */
    @Override
    public void validPost(Post post, boolean add) {
        //1.参数校验
        ThrowUtil.throwIf(post == null, ErrorCode.PARAMS_ERROR,"帖子不能为空");
        //2.获取数据
        Long id = post.getId();
        String title = post.getTitle();
        String content = post.getContent();
        String tags = post.getTags();
        Integer thumbNum = post.getThumbNum();
        Integer favourNum = post.getFavourNum();
        Long userId = post.getUserId();
        Date createTime = post.getCreateTime();
        Date updateTime = post.getUpdateTime();
        Integer isDelete = post.getIsDelete();
        //3.数据校验
        ThrowUtil.throwIf(ObjectUtils.isEmpty(title) || title.length() > 50
                , ErrorCode.PARAMS_ERROR,"标题不能为空且长度不能大于50");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(content) || content.length() > 10000
                ,ErrorCode.PARAMS_ERROR,"文章内容不可以为空或者超过10000");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(tags) || tags.length() > 100
                , ErrorCode.PARAMS_ERROR,"标签不能为空且长度不能大于100");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(userId) || userId < 0
                ,ErrorCode.PARAMS_ERROR,"用户id不能为空");
        ThrowUtil.throwIf(ObjectUtils.isEmpty(createTime),ErrorCode.PARAMS_ERROR,"创建时间不能为空");
        List<Integer> inputList = Arrays.asList(0,1);
        ThrowUtil.throwIf(isDelete == null || !inputList.stream().allMatch(num -> num == 0 || num == 1)
                ,ErrorCode.PARAMS_ERROR,"是否删除只能为0或者1");
        //4.新增类型增验
        if(add){
            ThrowUtil.throwIf(ObjectUtils.isNotEmpty(updateTime),ErrorCode.PARAMS_ERROR,"更新时间不能存在");
            ThrowUtil.throwIf(thumbNum != 0,ErrorCode.PARAMS_ERROR,"点赞数不能存在");
            ThrowUtil.throwIf(favourNum != 0,ErrorCode.PARAMS_ERROR,"收藏数不能存在");
        }
    }

    @Override
    public QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest) {
    }

    @Override
    public Page<Post> searchFromEs(PostQueryRequest postQueryRequest) {
        return null;
    }

    @Override
    public PostVO getPostVO(Post post, HttpServletRequest request) {
        return null;
    }

    @Override
    public Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request) {
        return null;
    }
}

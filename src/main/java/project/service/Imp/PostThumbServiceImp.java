package project.service.Imp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.mapper.PostThumbMapper;
import project.model.entity.Post;
import project.model.entity.PostThumb;
import project.model.entity.User;
import project.service.PostService;
import project.service.PostThumbService;
import project.service.UserService;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 帖子点赞业务
 */

@Service
@Slf4j
public class PostThumbServiceImp extends ServiceImpl<PostThumbMapper, PostThumb> implements PostThumbService {

    @Resource
    private PostService postService;

    /**
     * 点赞帖子
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostThumb(long postId, User loginUser) {
        //1.参数校验
        ThrowUtil.throwIf(postId <=0 , ErrorCode.PARAMS_ERROR, "帖子id错误");
        ThrowUtil.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        Long id = loginUser.getId();
        //2.贴子是否存在
        Post post = postService.getById(postId);
        ThrowUtil.throwIf(post == null, ErrorCode.PARAMS_ERROR, "帖子不存在");
        //Todo 串行点赞优化
        //3.串行点赞
        PostThumbService thumbService = (PostThumbService)AopContext.currentProxy();
        synchronized (String.valueOf(id).intern()){
            return thumbService.doPostThumbInner(id, postId);
        }
    }

    //Todo 是否可以优化
    @Override
    public int doPostThumbInner(long userId, long postId) {
        PostThumb postThumb = new PostThumb();
        postThumb.setUserId(userId);
        postThumb.setPostId(postId);

        boolean result;
        QueryWrapper<PostThumb> queryWrapper = new QueryWrapper<>(postThumb);
        PostThumb oldThumbStatus = this.getOne(queryWrapper);
        if(oldThumbStatus ==  null){
            result = this.save(postThumb);
            if (result) {
                result =postService.update().eq("id",postId).setSql("thumbNum = thumbNum + 1").update();
                return result ? -1:0;
            }else{
                throw new BusinessException(ErrorCode.OPERATION_ERROR);
            }
        }else{
            result = this.remove(queryWrapper);
            if(result){
                result = postService.update().eq("id",postId).gt("thumbNum",0).setSql("thumbNum = thumbNum - 1").update();
                return result ? 1:0;
            }else{
                throw new BusinessException(ErrorCode.OPERATION_ERROR);
            }
        }
    }
}

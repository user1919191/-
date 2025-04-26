package project.service.Imp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.mapper.PostFavourMapper;
import project.model.entity.Post;
import project.model.entity.PostFavour;
import project.model.entity.User;
import project.service.PostFavourService;
import project.service.PostService;
import project.service.PostThumbService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.print.ServiceUI;
import java.util.Date;

@Service
public class PostFavourServiceImp extends ServiceImpl<PostFavourMapper, PostFavour> implements PostFavourService {

    @Resource
    private PostService postService;

    /**
     * 收藏操作
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostFavour(long postId, User loginUser) {
        //1.参数校验
        ThrowUtil.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR,"用户未登录");
        ThrowUtil.throwIf(postId <= 0, ErrorCode.PARAMS_ERROR,"帖子id错误");
        Long id = loginUser.getId();
        PostFavourServiceImp currentProxy = (PostFavourServiceImp)AopContext.currentProxy();
        synchronized (String.valueOf(id).intern()){
            return currentProxy.doPostFavourInner(id, postId);
        }
    }

    /**
     * 分页查询收藏帖子
     * @param page
     * @param queryWrapper
     * @param favourUserId
     * @return
     */
    @Override
    public Page<Post> listFavourPostByPage(IPage<Post> page, Wrapper<Post> queryWrapper, long favourUserId) {
        if (favourUserId <= 0) {
            return new Page<>();
        }
        return baseMapper.listFavourPostByPage(page, queryWrapper, favourUserId);
    }

    /**
     * 收藏操作(内部方法)
     * @param userId
     * @param postId
     * @return
     */
    @Override
    public int doPostFavourInner(long userId, long postId) {
        PostFavour favour = new PostFavour();
        favour.setPostId(postId);
        favour.setUserId(userId);
        favour.setCreateTime(new Date());
        //2.判断是否已经收藏
        PostFavour postFavour = this.getById(postId);
        boolean result;
        if(postFavour == null){
            result = this.save(favour);
            if(result){
                result = postService.update().eq("id",postId).setSql("favourNum = favourNum + 1").update();
                return result ? 1:0;
            }else{
                throw new BusinessException(ErrorCode.OPERATION_ERROR);
            }
        }else{
            result = postService.update().eq("id",postId).gt("favourNum",0).setSql("favourNum = favourNum - 1").update();
            return result ? 1:0;
        }
    }
}

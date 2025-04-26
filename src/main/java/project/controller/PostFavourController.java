package project.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.bytebuddy.implementation.bytecode.Throw;
import org.redisson.api.RateIntervalUnit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.annotation.RateLimiter;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.constant.UserConstant;
import project.exception.BusinessException;
import project.exception.ThrowUtil;
import project.model.dto.post.PostQueryRequest;
import project.model.dto.postfavour.PostFavourAddRequest;
import project.model.dto.postfavour.PostFavourQueryRequest;
import project.model.entity.Post;
import project.model.entity.User;
import project.model.enums.LimitTypeEnum;
import project.model.vo.PostVO;
import project.service.PostFavourService;
import project.service.PostService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/post_favour")
public class PostFavourController {

    @Resource
    private PostFavourService postFavourService;

    @Resource
    private PostService postService;

    @Resource
    private UserService userService;

    /**
     * 收藏/取消收藏
     * @param addRequest
     * @param request
     * @return
     */
    @PostMapping("/")
    @SentinelResource(value = "doPostFavour",blockHandler = "BlockException",fallback = "doPostFavourFallback")
    public BaseResponse<Integer> doPostFavour(@RequestBody PostFavourAddRequest addRequest,HttpServletRequest request) {
        //1.参数校验
        ThrowUtil.throwIf(addRequest == null || addRequest.getPostId() <= 0, ErrorCode.PARAMS_ERROR,"参数错误");
        //2.登录校验
        if(userService.getLoginUser(request) == null){
            ThrowUtil.throwIf(true, ErrorCode.NOT_LOGIN,"用户未登录");
        }
        // 3.业务实现
        final User loginUser = userService.getLoginUser(request);
        long postId = addRequest.getPostId();
        int result = postFavourService.doPostFavour(postId,loginUser);
        return ResultUtil.success(result);
    }

    /**
     * 获取用户收藏的帖子列表
     * @param postQueryRequest
     * @param request
     */
    @PostMapping("/my/list/page")
    @RateLimiter(key = "listMyFavourPostPage",LimitCount = 10 ,CountTime = 2,timeUnit = RateIntervalUnit.SECONDS,limitType = LimitTypeEnum.REJECT_USER)
    @SentinelResource(value = "listMyFavourPostPage",blockHandler = "BlockException",fallback = "doPostFavourFallback")
    public BaseResponse<Page<PostVO>> listMyFavourPostPage(@RequestBody PostQueryRequest postQueryRequest,HttpServletRequest request) {
        //1.参数校验
        if (postQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        //2.获取分页
        long current = postQueryRequest.getPage();
        long size = postQueryRequest.getSize();
        Page<Post> postPage = postFavourService.listFavourPostByPage(new Page<>(current, size),
                postService.getQueryWrapper(postQueryRequest), loginUser.getId());
        return ResultUtil.success(postService.getPostVOPage(postPage, request));
    }

    /**
     * 获取用户收藏的帖子列表
     * @param postFavourQueryRequest
     * @param request
     */
    @PostMapping("/list/page")
    @SentinelResource(value = "listFavourPostPage",blockHandler = "BlockException",fallback = "doPostFavourFallback")
    public BaseResponse<Page<PostVO>> listFavourPostPage(@RequestBody PostFavourQueryRequest postFavourQueryRequest,HttpServletRequest request) {
        //1.参数校验
        if (postFavourQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //2.获取分页
        long current = postFavourQueryRequest.getPage();
        long size = postFavourQueryRequest.getSize();
        Long userId = postFavourQueryRequest.getUserId();
        Page<Post> postPage = postFavourService.listFavourPostByPage(new Page<>(current, size),
                postService.getQueryWrapper(postFavourQueryRequest.getPostQueryRequest()), userId);
        return ResultUtil.success(postService.getPostVOPage(postPage,request));
    }

    /**
     * 降级策略
     */
    public BaseResponse<Integer> doPostFavourFallback(PostFavourAddRequest addRequest,HttpServletRequest request,Throwable e) {
        return ResultUtil.success(0);
    }

    /**
     * 限流策略
     */
    public BaseResponse<Integer> BlockException(PostFavourAddRequest addRequest, HttpServletRequest request, BlockException e) {
        return ResultUtil.success(0);
    }
}

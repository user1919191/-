package project.controller;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.exception.ThrowUtil;
import project.model.dto.DummyInterview.interViewMutiRequest;
import project.model.entity.User;
import project.service.DummyInterViewService;
import project.service.UserService;
import project.model.dto.DummyInterview.interViewRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 模拟面试接口
 */
@RestController
@RequestMapping("/interview")
@Slf4j
public class DummyInterviewController {

    @Resource
    private UserService userService;

    @Resource
    private DummyInterViewService interViewService;

    /**
     * 直接调用AI大模型面试模拟
     * @param interViewRequest
     * @param request
     * @return
     */
    @PostMapping("/test")
    public BaseResponse<String> interview(@RequestBody interViewRequest interViewRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(interViewRequest == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.获取当前用户ID
        User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(ObjectUtils.isEmpty(loginUser),ErrorCode.NOT_ROLE,"请登录后使用");
        Long userId = loginUser.getId();
        //2.调用方法
        String answer = interViewService.interview(interViewRequest, userId);
        return ResultUtil.success(answer);
    }

    /**
     * 调用RAG本地向量库面试模拟
     * @param interViewRequest
     * @param request
     * @return
     */
    @PostMapping("/test/rag")
    public BaseResponse<String> interviewByRAG(@RequestBody interViewRequest interViewRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(interViewRequest == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.获取当前参数
        User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(ObjectUtils.isEmpty(loginUser),ErrorCode.NOT_ROLE,"请登录后使用");
        Long userId = loginUser.getId();
        //3.调用方法
        String answer = interViewService.interviewByRAG(interViewRequest, userId);
        return ResultUtil.success(answer);
    }

    /**
     * 调用多模态AI针对简历模拟面试
     * @param request
     * @return
     */
    @PostMapping("/test/multi")
    //todo https://img.alicdn.com/imgextra/i2/O1CN01ktT8451iQutqReELT_!!6000000004408-0-tps-689-487.jpg 仅仅支持此类格式
    public BaseResponse<String> multiModalInterview(@RequestBody interViewMutiRequest interViewMutiRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(interViewMutiRequest == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.获取当前参数
        User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(ObjectUtils.isEmpty(loginUser),ErrorCode.NOT_ROLE,"请登录后使用");
        Long userId = loginUser.getId();
        //3.调用方法
        String answer = interViewService.interviewByMuti(interViewMutiRequest, userId);
        return ResultUtil.success(answer);
    }

    /**
     * 使用多模态优化简历
     * @param interViewMutiRequest
     * @param request
     * @return
     */
    @PostMapping("/test/muti/update")
    public BaseResponse<String> updateMutiInterview(@RequestBody interViewMutiRequest interViewMutiRequest, HttpServletRequest request){
        //1.参数校验
        ThrowUtil.throwIf(interViewMutiRequest == null, ErrorCode.PARAMS_ERROR,"参数不能为空");
        //2.获取当前参数
        User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(ObjectUtils.isEmpty(loginUser),ErrorCode.NOT_ROLE,"请登录后使用");
        Long userId = loginUser.getId();
        //3.调用方法
        String answer = interViewService.interviewUpdateByPNG(interViewMutiRequest, userId);
        return ResultUtil.success(answer);
    }
}

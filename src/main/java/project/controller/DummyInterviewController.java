package project.controller;

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
import project.model.dto.DummyInterview.interViewRequest;
import project.model.entity.User;
import project.service.DummyInterViewService;
import project.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/interview")
public class DummyInterviewController {

    @Resource
    private UserService userService;

    @Resource
    private DummyInterViewService interViewService;

    @PostMapping("/test")
    public BaseResponse<String> interview(@RequestBody interViewRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interview(interviewRequest, userId));
    }

    @PostMapping("/test/rag")
    public BaseResponse<String> interviewByRag(@RequestBody interViewRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interviewByRAG(interviewRequest, userId));
    }

    @PostMapping("/test/multi")
    public BaseResponse<String> multiModalInterview(@RequestBody interViewMutiRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interviewByMuti(interviewRequest, userId));
    }

    @PostMapping("/test/muti/update")
    public BaseResponse<String> updateMultiInterview(@RequestBody interViewMutiRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interviewUpdateByPNG(interviewRequest, userId));
    }

    private void requireRequest(Object requestBody) {
        ThrowUtil.throwIf(requestBody == null, ErrorCode.PARAMS_ERROR, "request body must not be null");
    }

    private Long requireLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtil.throwIf(ObjectUtils.isEmpty(loginUser), ErrorCode.NOT_ROLE, "please login first");
        return loginUser.getId();
    }
}

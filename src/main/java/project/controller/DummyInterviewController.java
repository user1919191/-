package project.controller;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.common.BaseResponse;
import project.common.ErrorCode;
import project.common.ResultUtil;
import project.exception.ThrowUtil;
import project.model.dto.DummyInterview.InterviewAnswerRequest;
import project.model.dto.DummyInterview.InterviewRoundResultResponse;
import project.model.dto.DummyInterview.InterviewSessionCreateRequest;
import project.model.dto.DummyInterview.InterviewSessionDetailResponse;
import project.model.dto.DummyInterview.InterviewSessionReportResponse;
import project.model.dto.DummyInterview.ResumeAssetCreateRequest;
import project.model.dto.DummyInterview.ResumeAssetResponse;
import project.model.dto.DummyInterview.ResumeInterviewSessionCreateRequest;
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

    @PostMapping("/session/create/custom")
    public BaseResponse<InterviewRoundResultResponse> createCustomSession(@RequestBody InterviewSessionCreateRequest requestBody,
                                                                          HttpServletRequest request) {
        requireRequest(requestBody);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.createCustomInterview(requestBody, userId));
    }

    @PostMapping("/session/create/random")
    public BaseResponse<InterviewRoundResultResponse> createRandomSession(@RequestBody InterviewSessionCreateRequest requestBody,
                                                                          HttpServletRequest request) {
        requireRequest(requestBody);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.createRandomInterview(requestBody, userId));
    }

    @PostMapping("/session/create/resume")
    public BaseResponse<InterviewRoundResultResponse> createResumeSession(@RequestBody ResumeInterviewSessionCreateRequest requestBody,
                                                                          HttpServletRequest request) {
        requireRequest(requestBody);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.createResumeTargetedInterview(requestBody, userId));
    }

    @PostMapping("/session/answer")
    public BaseResponse<InterviewRoundResultResponse> submitAnswer(@RequestBody InterviewAnswerRequest requestBody,
                                                                   HttpServletRequest request) {
        requireRequest(requestBody);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.submitAnswer(requestBody, userId));
    }

    @GetMapping("/session/detail")
    public BaseResponse<InterviewSessionDetailResponse> getSessionDetail(@RequestParam String interviewId,
                                                                         HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.getSessionDetail(interviewId, userId));
    }

    @GetMapping("/session/report")
    public BaseResponse<InterviewSessionReportResponse> getSessionReport(@RequestParam String interviewId,
                                                                         HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.getSessionReport(interviewId, userId));
    }

    @PostMapping("/resume/asset/create")
    public BaseResponse<ResumeAssetResponse> createResumeAsset(@RequestBody ResumeAssetCreateRequest requestBody,
                                                               HttpServletRequest request) {
        requireRequest(requestBody);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.createResumeAsset(requestBody, userId));
    }

    @GetMapping("/resume/asset/detail")
    public BaseResponse<ResumeAssetResponse> getResumeAsset(@RequestParam Long resumeAssetId,
                                                            HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.getResumeAsset(resumeAssetId, userId));
    }

    @PostMapping("/test")
    public BaseResponse<InterviewRoundResultResponse> interview(@RequestBody interViewRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interview(interviewRequest, userId));
    }

    @PostMapping("/test/rag")
    public BaseResponse<InterviewRoundResultResponse> interviewByRag(@RequestBody interViewRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interviewByRAG(interviewRequest, userId));
    }

    @PostMapping("/test/multi")
    public BaseResponse<InterviewRoundResultResponse> multiModalInterview(@RequestBody interViewMutiRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interviewByMuti(interviewRequest, userId));
    }

    @PostMapping("/test/muti/update")
    public BaseResponse<InterviewRoundResultResponse> updateMultiInterview(@RequestBody interViewMutiRequest interviewRequest, HttpServletRequest request) {
        requireRequest(interviewRequest);
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.interviewUpdateByPNG(interviewRequest, userId));
    }

    @GetMapping("/round/result")
    public BaseResponse<InterviewRoundResultResponse> getRoundResult(@RequestParam String interviewId,
                                                                     @RequestParam Long roundId,
                                                                     HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        return ResultUtil.success(interViewService.getRoundResult(interviewId, roundId, userId));
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

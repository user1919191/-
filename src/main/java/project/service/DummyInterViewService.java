package project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import project.model.dto.DummyInterview.InterviewAnswerRequest;
import project.model.dto.DummyInterview.InterviewSessionReportResponse;
import project.model.dto.DummyInterview.InterviewRoundResultResponse;
import project.model.dto.DummyInterview.InterviewSessionCreateRequest;
import project.model.dto.DummyInterview.InterviewSessionDetailResponse;
import project.model.dto.DummyInterview.ResumeAssetCreateRequest;
import project.model.dto.DummyInterview.ResumeAssetResponse;
import project.model.dto.DummyInterview.ResumeInterviewSessionCreateRequest;
import project.model.dto.DummyInterview.ResumeProfile;
import project.model.dto.DummyInterview.interViewMutiRequest;
import project.model.dto.DummyInterview.interViewRequest;
import project.model.entity.InterView;

public interface DummyInterViewService extends IService<InterView> {

    InterviewRoundResultResponse createCustomInterview(InterviewSessionCreateRequest request, Long userId);

    InterviewRoundResultResponse createRandomInterview(InterviewSessionCreateRequest request, Long userId);

    InterviewRoundResultResponse createResumeTargetedInterview(ResumeInterviewSessionCreateRequest request, Long userId);

    InterviewRoundResultResponse submitAnswer(InterviewAnswerRequest request, Long userId);

    InterviewSessionDetailResponse getSessionDetail(String interviewId, Long userId);

    InterviewSessionReportResponse getSessionReport(String interviewId, Long userId);

    ResumeAssetResponse createResumeAsset(ResumeAssetCreateRequest request, Long userId);

    ResumeAssetResponse getResumeAsset(Long resumeAssetId, Long userId);

    ResumeProfile parseResumeImage(String resumeImage);

    InterviewRoundResultResponse interview(interViewRequest interviewrequest, Long userid);

    InterviewRoundResultResponse interviewByRAG(interViewRequest interviewrequest, Long userid);

    InterviewRoundResultResponse interviewByMuti(interViewMutiRequest interviewrequest, Long userid);

    InterviewRoundResultResponse interviewUpdateByPNG(interViewMutiRequest interviewrequest, Long userid);

    InterviewRoundResultResponse getRoundResult(String interviewId, Long roundId, Long userId);
}

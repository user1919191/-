package project.service.Imp;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.exception.ThrowUtil;
import project.manager.DashScopeAiManager;
import project.manager.InterviewSessionManager;
import project.mapper.InterviewRoundMapper;
import project.model.dto.DummyInterview.InterviewAnswerRequest;
import project.mapper.interViewMapper;
import project.model.dto.DummyInterview.CandidateSkillProfile;
import project.model.dto.DummyInterview.InterviewEvaluationResult;
import project.model.dto.DummyInterview.InterviewMessageRecord;
import project.model.dto.DummyInterview.InterviewRoundResultResponse;
import project.model.dto.DummyInterview.InterviewRoundState;
import project.model.dto.DummyInterview.InterviewSession;
import project.model.dto.DummyInterview.InterviewSessionCreateRequest;
import project.model.dto.DummyInterview.InterviewSessionDetailResponse;
import project.model.dto.DummyInterview.InterviewSessionReportResponse;
import project.model.dto.DummyInterview.QuestionPlan;
import project.model.dto.DummyInterview.ResumeAssetCreateRequest;
import project.model.dto.DummyInterview.ResumeAssetResponse;
import project.model.dto.DummyInterview.ResumeInterviewSessionCreateRequest;
import project.model.dto.DummyInterview.ResumeProfile;
import project.model.dto.DummyInterview.interViewMutiRequest;
import project.model.dto.DummyInterview.interViewRequest;
import project.model.entity.InterView;
import project.model.entity.InterviewRound;
import project.model.enums.AiTaskType;
import project.service.AiStructuredOutputService;
import project.service.agent.AgentRagOrchestrator;
import project.service.agent.RoundAssessmentContextAssembler;
import project.service.CandidateSkillProfiler;
import project.service.DummyInterViewService;
import project.service.InterviewAsyncApplicationService;
import project.service.InterviewDecisionService;
import project.service.InterviewEvaluator;
import project.service.InterviewPlanService;
import project.service.InterviewReplyRenderService;
import project.service.InterviewReportService;
import project.service.LocalInterviewQuestionStrategyService;
import project.service.PromptTemplateService;
import project.service.ResumeAssetService;
import project.model.dto.agent.RoundAssessmentContext;
import project.model.dto.rag.RagRetrievalResult;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DummyInterViewServiceImp extends ServiceImpl<interViewMapper, InterView> implements DummyInterViewService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String SCENE_STANDARD = "STANDARD";
    private static final String SCENE_RAG = "RAG";
    private static final String SCENE_RESUME_INTERVIEW = "RESUME_INTERVIEW";
    private static final String SCENE_RESUME_OPTIMIZE = "RESUME_OPTIMIZE";

    private static final String MODE_CUSTOM = "CUSTOM";
    private static final String MODE_RANDOM = "RANDOM";
    private static final String MODE_RESUME_TARGETED = "RESUME_TARGETED";

    private static final int MAX_HISTORY_MESSAGES = 16;

    @Resource
    private InterviewSessionManager interviewSessionManager;

    @Resource
    private InterviewEvaluator interviewEvaluator;

    @Resource
    private RoundAssessmentContextAssembler roundAssessmentContextAssembler;

    @Resource
    private AgentRagOrchestrator agentRagOrchestrator;

    @Resource
    private InterviewDecisionService interviewDecisionService;

    @Resource
    private CandidateSkillProfiler candidateSkillProfiler;

    @Resource
    private InterviewReplyRenderService interviewReplyRenderService;

    @Resource
    private LocalInterviewQuestionStrategyService localInterviewQuestionStrategyService;

    @Resource
    private InterviewAsyncApplicationService interviewAsyncApplicationService;

    @Resource
    private DashScopeAiManager dashScopeAiManager;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private AiStructuredOutputService aiStructuredOutputService;

    @Resource
    private InterviewRoundMapper interviewRoundMapper;

    @Resource
    private InterviewPlanService interviewPlanService;

    @Resource
    private ResumeAssetService resumeAssetService;

    @Resource
    private InterviewReportService interviewReportService;

    @Override
    public InterviewRoundResultResponse createCustomInterview(InterviewSessionCreateRequest request, Long userId) {
        validateSessionCreateRequest(request);
        request.setInterviewId(normalizeInterviewId(request.getInterviewId()));
        InterviewSession existing = interviewSessionManager.getSession(userId, request.getInterviewId());
        if (existing != null) {
            return buildCurrentSessionRoundResponse(existing);
        }
        return handleRound(userId, request.getInterviewId(), null, SCENE_STANDARD, null, MODE_CUSTOM, request, null);
    }

    @Override
    public InterviewRoundResultResponse createRandomInterview(InterviewSessionCreateRequest request, Long userId) {
        validateSessionCreateRequest(request);
        request.setInterviewId(normalizeInterviewId(request.getInterviewId()));
        InterviewSession existing = interviewSessionManager.getSession(userId, request.getInterviewId());
        if (existing != null) {
            return buildCurrentSessionRoundResponse(existing);
        }
        return handleRound(userId, request.getInterviewId(), null, SCENE_STANDARD, null, MODE_RANDOM, request, null);
    }

    @Override
    public InterviewRoundResultResponse createResumeTargetedInterview(ResumeInterviewSessionCreateRequest request, Long userId) {
        validateResumeSessionCreateRequest(request);
        request.setInterviewId(normalizeInterviewId(request.getInterviewId()));
        InterviewSession existing = interviewSessionManager.getSession(userId, request.getInterviewId());
        if (existing != null) {
            return buildCurrentSessionRoundResponse(existing);
        }
        return handleRound(userId, request.getInterviewId(), null, SCENE_RESUME_INTERVIEW, request.getResumeImage(), MODE_RESUME_TARGETED, null, request);
    }

    @Override
    public InterviewRoundResultResponse submitAnswer(InterviewAnswerRequest request, Long userId) {
        ThrowUtil.throwIf(request == null, ErrorCode.PARAMS_ERROR, "request is required");
        ThrowUtil.throwIf(StringUtils.isBlank(request.getInterviewId()), ErrorCode.PARAMS_ERROR, "interviewId is required");
        ThrowUtil.throwIf(StringUtils.isBlank(request.getAnswer()), ErrorCode.PARAMS_ERROR, "answer is required");
        InterviewSession session = interviewSessionManager.getSession(userId, request.getInterviewId());
        ThrowUtil.throwIf(session == null, ErrorCode.NOT_FOUND, "interview session not found");
        ThrowUtil.throwIf("FINISHED".equalsIgnoreCase(session.getSessionStatus()), ErrorCode.OPERATION_ERROR, "interview session already finished");
        return handleRound(userId, request.getInterviewId(), request.getAnswer(), session.getScene(), null, session.getMode(), null, null);
    }

    @Override
    public InterviewSessionDetailResponse getSessionDetail(String interviewId, Long userId) {
        ThrowUtil.throwIf(StringUtils.isBlank(interviewId), ErrorCode.PARAMS_ERROR, "interviewId is required");
        InterviewSession session = interviewSessionManager.getSession(userId, interviewId);
        ThrowUtil.throwIf(session == null, ErrorCode.NOT_FOUND, "interview session not found");
        return buildSessionDetailResponse(session);
    }

    @Override
    public InterviewSessionReportResponse getSessionReport(String interviewId, Long userId) {
        ThrowUtil.throwIf(StringUtils.isBlank(interviewId), ErrorCode.PARAMS_ERROR, "interviewId is required");
        return interviewReportService.buildReport(userId, interviewId);
    }

    @Override
    public ResumeAssetResponse createResumeAsset(ResumeAssetCreateRequest request, Long userId) {
        return resumeAssetService.createFromImage(userId, request);
    }

    @Override
    public ResumeAssetResponse getResumeAsset(Long resumeAssetId, Long userId) {
        return resumeAssetService.getAssetDetail(userId, resumeAssetId);
    }

    @Override
    public ResumeProfile parseResumeImage(String resumeImage) {
        return extractResumeProfile(resumeImage);
    }

    @Override
    public InterviewRoundResultResponse interview(interViewRequest interviewrequest, Long userid) {
        validateInterviewRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_STANDARD, null, MODE_CUSTOM, null, null);
    }

    @Override
    public InterviewRoundResultResponse interviewByRAG(interViewRequest interviewrequest, Long userid) {
        validateInterviewRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_RAG, null, MODE_CUSTOM, null, null);
    }

    @Override
    public InterviewRoundResultResponse interviewByMuti(interViewMutiRequest interviewrequest, Long userid) {
        validateMultiRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_RESUME_INTERVIEW,
                interviewrequest.getResumeImage(), MODE_RESUME_TARGETED, null, null);
    }

    @Override
    public InterviewRoundResultResponse interviewUpdateByPNG(interViewMutiRequest interviewrequest, Long userid) {
        validateMultiRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_RESUME_OPTIMIZE,
                interviewrequest.getResumeImage(), MODE_RESUME_TARGETED, null, null);
    }

    @Override
    public InterviewRoundResultResponse getRoundResult(String interviewId, Long roundId, Long userId) {
        return interviewAsyncApplicationService.getRoundResult(userId, interviewId, roundId);
    }

    private InterviewRoundResultResponse handleRound(Long userId,
                                                     String interviewId,
                                                     String userAnswer,
                                                     String scene,
                                                     String resumeImage,
                                                     String requestedMode,
                                                     InterviewSessionCreateRequest createRequest,
                                                     ResumeInterviewSessionCreateRequest resumeCreateRequest) {
        InterviewSession session = interviewSessionManager.getSession(userId, interviewId);
        if (session == null || !scene.equals(session.getScene())) {
            session = createSession(userId, interviewId, scene, resumeImage, requestedMode, createRequest, resumeCreateRequest);
        } else if (needsResumeProfile(scene, session) && StringUtils.isNotBlank(resumeImage)) {
            session.setResumeProfile(extractResumeProfile(resumeImage));
        }

        boolean firstRound = session.getCompletedRounds() == null || session.getCompletedRounds() == 0;
        if (!firstRound) {
            ThrowUtil.throwIf(StringUtils.isBlank(userAnswer), ErrorCode.PARAMS_ERROR, "answer is required");
        }

        InterviewEvaluationResult evaluationResult = null;
        if (!firstRound) {
            String currentQuestion = getLastAskedQuestion(session);
            evaluationResult = shouldEndByUserInput(scene, userAnswer)
                    ? buildUserExitEvaluation(scene)
                    : evaluateMainFlow(session, userAnswer, currentQuestion);
            session.setCandidateSkillProfile(candidateSkillProfiler.updateProfile(session.getCandidateSkillProfile(), evaluationResult, scene));
            session.getHistory().add(new InterviewMessageRecord(ROLE_USER, userAnswer.trim()));
        }

        QuestionPlan decision = interviewDecisionService.decide(session, evaluationResult, firstRound);
        QuestionPlan questionPlan = "FINISH".equalsIgnoreCase(decision.getStatus())
                ? decision
                : localInterviewQuestionStrategyService.buildPlan(session, evaluationResult, decision, firstRound);

        String visibleReply = interviewReplyRenderService.render(
                scene,
                evaluationResult,
                questionPlan,
                session.getCandidateSkillProfile(),
                firstRound
        );
        session.getHistory().add(new InterviewMessageRecord(ROLE_ASSISTANT, visibleReply));
        updatePlanningState(session, questionPlan);
        trimHistory(session);

        InterviewRound round = persistHotRound(userId, interviewId, scene, userAnswer, session, evaluationResult, questionPlan, visibleReply, firstRound);
        updateSession(session, round, evaluationResult, questionPlan);
        interviewSessionManager.saveSession(session);

        interviewAsyncApplicationService.processRoundDetailAsync(userId, interviewId, round.getId(), scene);

        InterviewRoundResultResponse response = new InterviewRoundResultResponse();
        response.setMode(session.getMode());
        response.setInterviewId(interviewId);
        response.setSessionStatus(session.getSessionStatus());
        response.setRoundId(round.getId());
        response.setRoundNumber(round.getRoundNumber());
        response.setCompletedRounds(session.getCompletedRounds());
        response.setTaskId(round.getTaskId());
        response.setTaskStatus(round.getTaskStatus());
        response.setWorkflowStatus(round.getWorkflowStatus());
        response.setDecisionType(round.getDecisionType());
        response.setOverallScore(round.getOverallScore());
        response.setSummary(round.getSummary());
        response.setVisibleReply(visibleReply);
        response.setCurrentQuestion(questionPlan.getQuestion());
        response.setDetailReady(false);
        response.setPlanSummary(session.getPlanSummary());
        response.setFocusPoints(evaluationResult == null ? new ArrayList<>() : evaluationResult.getFocusPoints());
        return response;
    }

    private InterviewRound persistHotRound(Long userId,
                                           String interviewId,
                                           String scene,
                                           String userAnswer,
                                           InterviewSession session,
                                           InterviewEvaluationResult evaluationResult,
                                           QuestionPlan questionPlan,
                                           String visibleReply,
                                           boolean firstRound) {
        InterviewRound round = new InterviewRound();
        round.setInterviewId(interviewId);
        round.setUserId(userId);
        round.setRoundNumber((session.getCompletedRounds() == null ? 0 : session.getCompletedRounds()) + 1);
        round.setScene(scene);
        round.setQuestion(firstRound ? questionPlan.getQuestion() : getLastAskedQuestion(session));
        round.setCandidateAnswer(StringUtils.trimToEmpty(userAnswer));
        round.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        round.setTaskStatus("PENDING");
        round.setWorkflowStatus("DETAIL_PENDING");
        round.setDecisionType(questionPlan.getDecisionType());
        round.setOverallScore(evaluationResult == null ? null : evaluationResult.getOverallScore());
        round.setSummary(evaluationResult == null ? questionPlan.getStrategyReason() : evaluationResult.getSummary());
        round.setVisibleReply(visibleReply);
        round.setRetrievalCount(0);
        round.setVerificationLoopCount(0);
        round.setCreatedAt(new Date());
        round.setUpdatedAt(new Date());
        interviewRoundMapper.insert(round);
        return round;
    }

    private void updateSession(InterviewSession session,
                               InterviewRound round,
                               InterviewEvaluationResult evaluationResult,
                               QuestionPlan questionPlan) {
        long now = System.currentTimeMillis();
        session.setUpdatedAt(now);
        session.setCompletedRounds(round.getRoundNumber());
        session.setCurrentRoundId(String.valueOf(round.getId()));
        session.setCurrentTaskId(round.getTaskId());
        session.setLatestDecisionType(questionPlan.getDecisionType());
        session.setRoundIndex(round.getRoundNumber());
        session.setSessionStatus("ACTIVE");
        if (evaluationResult != null && evaluationResult.getOverallScore() != null) {
            session.setTotalScore((session.getTotalScore() == null ? 0 : session.getTotalScore()) + evaluationResult.getOverallScore());
            if (evaluationResult.getOverallScore() < 40) {
                session.setContinuousLowScoreRounds((session.getContinuousLowScoreRounds() == null ? 0 : session.getContinuousLowScoreRounds()) + 1);
            } else {
                session.setContinuousLowScoreRounds(0);
            }
        }
        if ("FINISH".equalsIgnoreCase(questionPlan.getStatus())) {
            session.setSessionStatus("FINISHED");
            session.setFinishAllowed(true);
        }
        InterviewRoundState state = new InterviewRoundState();
        state.setRoundId(round.getId());
        state.setRoundNumber(round.getRoundNumber());
        state.setTaskId(round.getTaskId());
        state.setQuestion(round.getQuestion());
        state.setCandidateAnswer(round.getCandidateAnswer());
        state.setTaskStatus(round.getTaskStatus());
        state.setWorkflowStatus(round.getWorkflowStatus());
        state.setEvaluationResult(evaluationResult);
        state.setRoundDecision(questionPlan);
        state.setVisibleReply(round.getVisibleReply());
        state.setSubmittedAt(now);
        state.setCompletedAt(now);
        session.getRoundStates().add(state);
    }

    private InterviewSession createSession(Long userId,
                                           String interviewId,
                                           String scene,
                                           String resumeImage,
                                           String requestedMode,
                                           InterviewSessionCreateRequest createRequest,
                                           ResumeInterviewSessionCreateRequest resumeCreateRequest) {
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setInterviewId(interviewId);
        session.setScene(scene);
        session.setMode(StringUtils.defaultIfBlank(requestedMode, MODE_CUSTOM));
        session.setCandidateSkillProfile(new CandidateSkillProfile());
        long now = System.currentTimeMillis();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setSessionStatus("ACTIVE");
        session.setCompletedRounds(0);
        if (requiresResume(scene)) {
            if (resumeCreateRequest != null && resumeCreateRequest.getResumeAssetId() != null) {
                session.setResumeProfile(resumeAssetService.getResumeProfile(userId, resumeCreateRequest.getResumeAssetId()));
            } else {
                ThrowUtil.throwIf(StringUtils.isBlank(resumeImage), ErrorCode.PARAMS_ERROR, "resumeImage is required");
                session.setResumeProfile(extractResumeProfile(resumeImage));
            }
        }
        applySessionPlan(session, createRequest, resumeCreateRequest);
        return session;
    }

    private void applySessionPlan(InterviewSession session,
                                  InterviewSessionCreateRequest createRequest,
                                  ResumeInterviewSessionCreateRequest resumeCreateRequest) {
        if (MODE_RANDOM.equalsIgnoreCase(session.getMode())) {
            interviewPlanService.applyRandomPlan(session, createRequest);
            return;
        }
        if (MODE_RESUME_TARGETED.equalsIgnoreCase(session.getMode())) {
            interviewPlanService.applyResumeTargetedPlan(session, resumeCreateRequest, session.getResumeProfile());
            return;
        }
        InterviewSessionCreateRequest effectiveRequest = createRequest == null ? buildDefaultCreateRequest(session.getInterviewId()) : createRequest;
        interviewPlanService.applyCustomPlan(session, effectiveRequest);
    }

    private InterviewSessionCreateRequest buildDefaultCreateRequest(String interviewId) {
        InterviewSessionCreateRequest request = new InterviewSessionCreateRequest();
        request.setInterviewId(interviewId);
        request.setJobRole("JAVA_BACKEND_ENGINEER");
        request.setDifficulty("BASIC");
        request.setDurationMinutes(30);
        request.setDynamicDifficultyEnabled(true);
        request.setLanguages(List.of("JAVA"));
        request.setTechStacks(List.of("SPRING_BOOT", "JVM", "REDIS", "MYSQL"));
        return request;
    }

    private String getLastAskedQuestion(InterviewSession session) {
        if (session.getAskedQuestions() == null || session.getAskedQuestions().isEmpty()) {
            return "";
        }
        return session.getAskedQuestions().get(session.getAskedQuestions().size() - 1);
    }

    private InterviewEvaluationResult evaluateMainFlow(InterviewSession session, String userAnswer, String currentQuestion) {
        RoundAssessmentContext roundContext = roundAssessmentContextAssembler.assemble(session, userAnswer, currentQuestion);
        RagRetrievalResult retrievalResult = agentRagOrchestrator.retrieveForMainFlow(roundContext);
        InterviewRoundState roundState = new InterviewRoundState();
        roundState.setRoundNumber(roundContext.getRoundNumber());
        roundState.setQuestion(roundContext.getQuestion());
        roundState.setCandidateAnswer(roundContext.getCandidateAnswer());
        roundState.setRagQuery(roundContext.getRagQuery());
        roundState.setRetrievalAttempt(retrievalResult.getAttempt());
        roundState.setRetrievalStrategy(retrievalResult.getRetrievalStrategy());
        roundState.setEvidences(retrievalResult.getEvidences());
        return interviewEvaluator.evaluate(session, roundState, retrievalResult);
    }

    private ResumeProfile extractResumeProfile(String resumeImage) {
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("image", resumeImage);
        imagePart.put("max_pixels", 6422528);
        imagePart.put("min_pixels", 3136);
        imagePart.put("enable_rotate", true);

        MultiModalMessage message = MultiModalMessage.builder()
                .role(ROLE_USER)
                .content(Arrays.asList(
                        imagePart,
                        Collections.singletonMap("text", promptTemplateService.buildResumeExtractionPrompt())))
                .build();

        String rawResponse = dashScopeAiManager.generateMultiModalText(AiTaskType.OCR, Collections.singletonList(message));
        return aiStructuredOutputService.parseOrRepair(
                rawResponse,
                ResumeProfile.class,
                "AI returned invalid resume JSON"
        );
    }

    private boolean shouldEndByUserInput(String scene, String userAnswer) {
        String normalized = StringUtils.defaultString(userAnswer).trim();
        if (normalized.isEmpty()) {
            return false;
        }
        if (SCENE_RESUME_OPTIMIZE.equals(scene)) {
            return normalized.contains("浼樺寲缁撴潫");
        }
        return normalized.contains("闈㈣瘯缁撴潫");
    }

    private InterviewEvaluationResult buildUserExitEvaluation(String scene) {
        InterviewEvaluationResult result = new InterviewEvaluationResult();
        result.setSchemaVersion("evaluation-v2");
        result.setStatus("FINISH");
        result.setOverallScore(0);
        result.setRecommendedDifficulty(SCENE_RESUME_OPTIMIZE.equals(scene) ? "BASIC" : "INTERMEDIATE");
        result.setSummary("鐢ㄦ埛涓诲姩缁撴潫褰撳墠娴佺▼銆?");
        result.setOverallFeedback("鏈疆鏍规嵁鐢ㄦ埛鎰忔効缁撴潫銆?");
        result.setFinishReason("鐢ㄦ埛涓诲姩缁撴潫銆?");
        result.setCertainty("HIGH");
        result.setVerificationPassed(true);
        result.setEvidenceSufficient(true);
        result.setContainsHallucinationRisk(false);
        result.setDetailVersion("v2");
        return result;
    }

    private void updatePlanningState(InterviewSession session, QuestionPlan questionPlan) {
        if (questionPlan == null || !"ASK".equalsIgnoreCase(questionPlan.getStatus())) {
            return;
        }
        if (StringUtils.isNotBlank(questionPlan.getQuestion())) {
            session.getAskedQuestions().add(questionPlan.getQuestion());
        }
        if (questionPlan.getTopicTags() != null) {
            for (String topicTag : questionPlan.getTopicTags()) {
                if (StringUtils.isBlank(topicTag)) {
                    continue;
                }
                String trimmed = topicTag.trim();
                if (!session.getCoveredTopics().contains(trimmed)) {
                    session.getCoveredTopics().add(trimmed);
                }
            }
        }
    }

    private void trimHistory(InterviewSession session) {
        List<InterviewMessageRecord> history = session.getHistory();
        if (history == null || history.size() <= MAX_HISTORY_MESSAGES) {
            return;
        }
        int fromIndex = history.size() - MAX_HISTORY_MESSAGES;
        session.setHistory(new ArrayList<>(history.subList(fromIndex, history.size())));
    }

    private boolean requiresResume(String scene) {
        return SCENE_RESUME_INTERVIEW.equals(scene) || SCENE_RESUME_OPTIMIZE.equals(scene);
    }

    private boolean needsResumeProfile(String scene, InterviewSession session) {
        return requiresResume(scene) && session.getResumeProfile() == null;
    }

    private void validateInterviewRequest(interViewRequest request) {
        ThrowUtil.throwIf(request == null, ErrorCode.PARAMS_ERROR, "request is required");
        ThrowUtil.throwIf(StringUtils.isBlank(request.getInterviewId()), ErrorCode.PARAMS_ERROR, "interviewId is required");
    }

    private void validateMultiRequest(interViewMutiRequest request) {
        ThrowUtil.throwIf(request == null, ErrorCode.PARAMS_ERROR, "request is required");
        ThrowUtil.throwIf(StringUtils.isBlank(request.getInterviewId()), ErrorCode.PARAMS_ERROR, "interviewId is required");
    }

    private void validateSessionCreateRequest(InterviewSessionCreateRequest request) {
        ThrowUtil.throwIf(request == null, ErrorCode.PARAMS_ERROR, "request is required");
    }

    private void validateResumeSessionCreateRequest(ResumeInterviewSessionCreateRequest request) {
        ThrowUtil.throwIf(request == null, ErrorCode.PARAMS_ERROR, "request is required");
        boolean hasResumeAssetId = request.getResumeAssetId() != null;
        boolean hasResumeImage = StringUtils.isNotBlank(request.getResumeImage());
        ThrowUtil.throwIf(!hasResumeAssetId && !hasResumeImage,
                ErrorCode.PARAMS_ERROR,
                "resumeAssetId or resumeImage is required");
    }

    private String normalizeInterviewId(String interviewId) {
        return StringUtils.isBlank(interviewId) ? "iv_" + UUID.randomUUID().toString().replace("-", "") : interviewId.trim();
    }

    private InterviewRoundResultResponse buildCurrentSessionRoundResponse(InterviewSession session) {
        InterviewRoundResultResponse response = new InterviewRoundResultResponse();
        response.setMode(session.getMode());
        response.setInterviewId(session.getInterviewId());
        response.setSessionStatus(session.getSessionStatus());
        response.setCompletedRounds(session.getCompletedRounds());
        response.setPlanSummary(session.getPlanSummary());
        if (session.getRoundStates() != null && !session.getRoundStates().isEmpty()) {
            InterviewRoundState lastRound = session.getRoundStates().get(session.getRoundStates().size() - 1);
            response.setRoundId(lastRound.getRoundId());
            response.setRoundNumber(lastRound.getRoundNumber());
            response.setTaskId(lastRound.getTaskId());
            response.setTaskStatus(lastRound.getTaskStatus());
            response.setWorkflowStatus(lastRound.getWorkflowStatus());
            response.setVisibleReply(lastRound.getVisibleReply());
            QuestionPlan roundDecision = lastRound.getRoundDecision();
            if (roundDecision != null) {
                response.setDecisionType(roundDecision.getDecisionType());
                response.setCurrentQuestion(roundDecision.getQuestion());
            }
            if (lastRound.getEvaluationResult() != null) {
                response.setOverallScore(lastRound.getEvaluationResult().getOverallScore());
                response.setSummary(lastRound.getEvaluationResult().getSummary());
                response.setFocusPoints(lastRound.getEvaluationResult().getFocusPoints());
            } else {
                response.setFocusPoints(new ArrayList<>());
            }
        } else {
            response.setFocusPoints(new ArrayList<>());
        }
        response.setDetailReady(false);
        return response;
    }

    private InterviewSessionDetailResponse buildSessionDetailResponse(InterviewSession session) {
        InterviewSessionDetailResponse response = new InterviewSessionDetailResponse();
        response.setInterviewId(session.getInterviewId());
        response.setMode(session.getMode());
        response.setScene(session.getScene());
        response.setSessionStatus(session.getSessionStatus());
        response.setCompletedRounds(session.getCompletedRounds());
        response.setMinRounds(session.getMinRounds());
        response.setMaxRounds(session.getMaxRounds());
        response.setPreferredDifficulty(session.getPreferredDifficulty());
        response.setDurationMinutes(session.getDurationMinutes());
        response.setDynamicDifficultyEnabled(session.getDynamicDifficultyEnabled());
        response.setCurrentRoundId(session.getCurrentRoundId());
        response.setCurrentTaskId(session.getCurrentTaskId());
        response.setLatestDecisionType(session.getLatestDecisionType());
        response.setPlanSummary(session.getPlanSummary());
        response.setFinishAllowed(session.getFinishAllowed());
        response.setInterviewStyles(session.getInterviewStyles());
        response.setSelectedBaseTopics(session.getSelectedBaseTopics());
        response.setSelectedLanguages(session.getSelectedLanguages());
        response.setSelectedTechStacks(session.getSelectedTechStacks());
        if (session.getRoundStates() != null && !session.getRoundStates().isEmpty()) {
            InterviewRoundState lastRound = session.getRoundStates().get(session.getRoundStates().size() - 1);
            response.setLatestVisibleReply(lastRound.getVisibleReply());
            if (lastRound.getRoundDecision() != null) {
                response.setCurrentQuestion(lastRound.getRoundDecision().getQuestion());
            }
        }
        return response;
    }
}

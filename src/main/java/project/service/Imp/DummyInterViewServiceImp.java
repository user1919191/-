package project.service.Imp;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.exception.ThrowUtil;
import project.manager.DashScopeAiManager;
import project.manager.InterviewSessionManager;
import project.manager.RagManager;
import project.mapper.interViewMapper;
import project.model.dto.DummyInterview.CandidateSkillProfile;
import project.model.dto.DummyInterview.InterviewEvaluationResult;
import project.model.dto.DummyInterview.InterviewMessageRecord;
import project.model.dto.DummyInterview.InterviewSession;
import project.model.dto.DummyInterview.QuestionPlan;
import project.model.dto.DummyInterview.ResumeProfile;
import project.model.dto.DummyInterview.interViewMutiRequest;
import project.model.dto.DummyInterview.interViewRequest;
import project.model.entity.InterView;
import project.model.enums.AiTaskType;
import project.service.AiStructuredOutputService;
import project.service.CandidateSkillProfiler;
import project.service.DummyInterViewService;
import project.service.InterviewEvaluator;
import project.service.PromptTemplateService;
import project.service.QuestionPlanner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DummyInterViewServiceImp extends ServiceImpl<interViewMapper, InterView> implements DummyInterViewService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String SCENE_STANDARD = "STANDARD";
    private static final String SCENE_RAG = "RAG";
    private static final String SCENE_RESUME_INTERVIEW = "RESUME_INTERVIEW";
    private static final String SCENE_RESUME_OPTIMIZE = "RESUME_OPTIMIZE";

    private static final int MAX_HISTORY_MESSAGES = 16;

    @Resource
    private DashScopeAiManager dashScopeAiManager;

    @Resource
    private RagManager ragManager;

    @Resource
    private InterviewSessionManager interviewSessionManager;

    @Resource
    private InterviewEvaluator interviewEvaluator;

    @Resource
    private QuestionPlanner questionPlanner;

    @Resource
    private CandidateSkillProfiler candidateSkillProfiler;

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private AiStructuredOutputService aiStructuredOutputService;

    @Override
    public String interview(interViewRequest interviewrequest, Long userid) {
        validateInterviewRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_STANDARD, null);
    }

    @Override
    public String interviewByRAG(interViewRequest interviewrequest, Long userid) {
        validateInterviewRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_RAG, null);
    }

    @Override
    public String interviewByMuti(interViewMutiRequest interviewrequest, Long userid) {
        validateMultiRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_RESUME_INTERVIEW, interviewrequest.getResumeImage());
    }

    @Override
    public String interviewUpdateByPNG(interViewMutiRequest interviewrequest, Long userid) {
        validateMultiRequest(interviewrequest);
        return handleRound(userid, interviewrequest.getInterviewId(), interviewrequest.getAnswer(), SCENE_RESUME_OPTIMIZE, interviewrequest.getResumeImage());
    }

    private String handleRound(Long userId, String interviewId, String userAnswer, String scene, String resumeImage) {
        InterviewSession session = interviewSessionManager.getSession(userId, interviewId);
        if (session == null || !scene.equals(session.getScene())) {
            session = createSession(userId, interviewId, scene, resumeImage);
        } else if (needsResumeProfile(scene, session) && StringUtils.isNotBlank(resumeImage)) {
            session.setResumeProfile(extractResumeProfile(resumeImage));
        }

        boolean firstRound = session.getAskedQuestions().isEmpty();
        if (!firstRound) {
            ThrowUtil.throwIf(StringUtils.isBlank(userAnswer), ErrorCode.PARAMS_ERROR, "answer is required");
        }

        String ragContext = buildRoundRagContext(session, userAnswer, firstRound);
        InterviewEvaluationResult evaluationResult = null;

        if (!firstRound) {
            evaluationResult = shouldEndByUserInput(scene, userAnswer)
                    ? buildUserExitEvaluation(scene)
                    : interviewEvaluator.evaluate(session, userAnswer, ragContext);
            session.setCandidateSkillProfile(candidateSkillProfiler.updateProfile(session.getCandidateSkillProfile(), evaluationResult, scene));
            session.getHistory().add(new InterviewMessageRecord(ROLE_USER, userAnswer.trim()));
        }

        QuestionPlan questionPlan = shouldFinish(evaluationResult)
                ? buildFinishPlan(evaluationResult)
                : questionPlanner.plan(session, evaluationResult, ragContext, firstRound);

        if (!firstRound && StringUtils.isBlank(questionPlan.getDifficulty())) {
            questionPlan.setDifficulty(session.getCandidateSkillProfile().getCurrentDifficulty());
        }
        if (StringUtils.isNotBlank(questionPlan.getDifficulty())) {
            session.getCandidateSkillProfile().setCurrentDifficulty(questionPlan.getDifficulty());
        }

        String visibleReply = renderRoundResponse(scene, evaluationResult, questionPlan, session.getCandidateSkillProfile(), firstRound);
        session.getHistory().add(new InterviewMessageRecord(ROLE_ASSISTANT, visibleReply));
        updatePlanningState(session, questionPlan);
        trimHistory(session);

        long now = System.currentTimeMillis();
        if (session.getCreatedAt() == null) {
            session.setCreatedAt(now);
        }
        session.setUpdatedAt(now);
        session.setRoundIndex(session.getRoundIndex() == null ? 1 : session.getRoundIndex() + 1);

        if (shouldDeleteSession(questionPlan, evaluationResult)) {
            interviewSessionManager.deleteSession(userId, interviewId);
        } else {
            interviewSessionManager.saveSession(session);
        }
        return visibleReply;
    }

    private InterviewSession createSession(Long userId, String interviewId, String scene, String resumeImage) {
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setInterviewId(interviewId);
        session.setScene(scene);
        session.setCandidateSkillProfile(new CandidateSkillProfile());
        long now = System.currentTimeMillis();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        if (requiresResume(scene)) {
            ThrowUtil.throwIf(StringUtils.isBlank(resumeImage), ErrorCode.PARAMS_ERROR, "resumeImage is required");
            session.setResumeProfile(extractResumeProfile(resumeImage));
        }
        return session;
    }

    private String buildRoundRagContext(InterviewSession session, String userAnswer, boolean firstRound) {
        if (!SCENE_RAG.equals(session.getScene())) {
            return "";
        }
        if (firstRound) {
            return ragManager.buildContextBlock("Java backend interview opening question covering Java, Redis, MySQL, MQ and distributed systems.");
        }
        String lastQuestion = session.getAskedQuestions().isEmpty() ? "" : session.getAskedQuestions().get(session.getAskedQuestions().size() - 1);
        String retrievalQuery = "Previous question:\n" + lastQuestion + "\nCandidate answer:\n" + StringUtils.defaultString(userAnswer);
        return ragManager.buildContextBlock(retrievalQuery);
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
            return normalized.contains("优化结束");
        }
        return normalized.contains("面试结束");
    }

    private InterviewEvaluationResult buildUserExitEvaluation(String scene) {
        InterviewEvaluationResult result = new InterviewEvaluationResult();
        result.setStatus("FINISH");
        result.setOverallScore(0);
        result.setRecommendedDifficulty(SCENE_RESUME_OPTIMIZE.equals(scene) ? "BASIC" : "INTERMEDIATE");
        result.setSummary("用户主动结束当前流程。");
        result.setOverallFeedback("本轮根据用户意愿结束。");
        result.setFinishReason("用户主动结束。");
        return result;
    }

    private QuestionPlan buildFinishPlan(InterviewEvaluationResult evaluationResult) {
        QuestionPlan questionPlan = new QuestionPlan();
        questionPlan.setStatus("FINISH");
        questionPlan.setDifficulty(evaluationResult == null ? "BASIC" : evaluationResult.getRecommendedDifficulty());
        questionPlan.setFinishReason(evaluationResult == null ? "" : evaluationResult.getFinishReason());
        return questionPlan;
    }

    private boolean shouldFinish(InterviewEvaluationResult evaluationResult) {
        return evaluationResult != null && "FINISH".equalsIgnoreCase(evaluationResult.getStatus());
    }

    private boolean shouldDeleteSession(QuestionPlan questionPlan, InterviewEvaluationResult evaluationResult) {
        if (questionPlan != null && "FINISH".equalsIgnoreCase(questionPlan.getStatus())) {
            return true;
        }
        return evaluationResult != null && "FINISH".equalsIgnoreCase(evaluationResult.getStatus());
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
                if (StringUtils.isBlank(topicTag) || session.getCoveredTopics().contains(topicTag.trim())) {
                    continue;
                }
                session.getCoveredTopics().add(topicTag.trim());
            }
        }
    }

    private String renderRoundResponse(
            String scene,
            InterviewEvaluationResult evaluationResult,
            QuestionPlan questionPlan,
            CandidateSkillProfile profile,
            boolean firstRound
    ) {
        List<String> blocks = new ArrayList<>();
        if (!firstRound && evaluationResult != null) {
            blocks.add("\u672c\u8f6e\u5f97\u5206\uff1a" + evaluationResult.getOverallScore() + "/100");
            if (StringUtils.isNotBlank(evaluationResult.getSummary())) {
                blocks.add("\u8bc4\u4ef7\uff1a" + evaluationResult.getSummary());
            }
            if (!evaluationResult.getStrengths().isEmpty()) {
                blocks.add("\u4f18\u52bf\uff1a" + String.join("\uff1b", evaluationResult.getStrengths()));
            }
            if (!evaluationResult.getWeaknesses().isEmpty()) {
                blocks.add("\u5f85\u63d0\u5347\uff1a" + String.join("\uff1b", evaluationResult.getWeaknesses()));
            }
            if (!evaluationResult.getFocusPoints().isEmpty()) {
                blocks.add("\u5173\u6ce8\u70b9\uff1a" + String.join("\uff1b", evaluationResult.getFocusPoints()));
            }
        }
        String profileSummary = buildProfileSummary(profile, scene);
        if (StringUtils.isNotBlank(profileSummary)) {
            blocks.add("\u80fd\u529b\u753b\u50cf\uff1a" + profileSummary);
        }
        if (StringUtils.isNotBlank(profile.getCurrentDifficulty())) {
            blocks.add("\u96be\u5ea6\uff1a" + profile.getCurrentDifficulty());
        }
        if (questionPlan != null && "FINISH".equalsIgnoreCase(questionPlan.getStatus())) {
            if (evaluationResult != null && StringUtils.isNotBlank(evaluationResult.getOverallFeedback())) {
                blocks.add("\u603b\u4f53\u8bc4\u4ef7\uff1a" + evaluationResult.getOverallFeedback());
            } else if (StringUtils.isNotBlank(questionPlan.getFinishReason())) {
                blocks.add("\u603b\u4f53\u8bc4\u4ef7\uff1a" + questionPlan.getFinishReason());
            }
        } else if (questionPlan != null && StringUtils.isNotBlank(questionPlan.getQuestion())) {
            String label = SCENE_RESUME_OPTIMIZE.equals(scene)
                    ? "\u8865\u5145\u95ee\u9898\uff1a"
                    : "\u4e0b\u4e00\u95ee\uff1a";
            blocks.add(label + questionPlan.getQuestion());
        }
        return String.join("\n\n", blocks);
    }

    private String buildProfileSummary(CandidateSkillProfile profile, String scene) {
        if (profile == null || profile.getDimensionScores() == null || profile.getDimensionScores().isEmpty()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : profile.getDimensionScores().entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            items.add(entry.getKey() + " " + entry.getValue());
            if (items.size() >= (SCENE_RESUME_OPTIMIZE.equals(scene) ? 4 : 5)) {
                break;
            }
        }
        return String.join("\uff1b", items);
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
}

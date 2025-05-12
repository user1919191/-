package project.service.Imp;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.exception.ThrowUtil;
import project.manager.RagManager;
import project.mapper.interViewMapper;
import project.model.dto.DummyInterview.interViewDTO;
import project.model.dto.DummyInterview.interViewMutiRequest;
import project.model.dto.DummyInterview.interViewRequest;
import project.model.entity.InterView;
import project.service.DummyInterViewService;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 面试服务实现类
 */
@Service
@Slf4j
public class DummyInterViewServiceImp extends ServiceImpl<interViewMapper, InterView> implements DummyInterViewService {

    /**
     * 对话暂存列表
     */
    private Map<String, List<Message>> interviewTemplete = new ConcurrentHashMap<>();

    /**
     * 初始化系统提示
     */
    private static String InitSystemPrompt = "您是一为Java,包含Redis,MySQL,MQ等中间件领域的面试官," +
            "您要对面试者问好并进行面试,面试者会回答您的问题,您需要根据面试者的回答给出评价," +
            "并给出下一轮的问题,直到面试结束,面试结束时返回你的总体评价,面试结束的标志是面试者回答“面试结束(要求,纯文本格式输出)" +
            "如果面试者超过三次回答不相关内容,直接停止面试";

    private static String RAGInitSystemPrompt = "您是一为Java,包含Redis,MySQL,MQ等中间件领域的面试官," +
            "您要对进行面试(面试内容主要参照本地知识库)请结合上下文对话,可以对面试者进行追问.面试者会回答您的问题,您需要根据面试者的回答给出评价," +
            "并给出下一轮的问题,面试结束的标志是面试者回答“面试结束(要求,纯文本格式输出),不需要输出不必要的语气词或问候词" +
            "如果面试者超过三次回答不相关内容,直接停止面试";

    private static String MutiInitSystemPrompt = "您是一为Java,包含Redis,MySQL,MQ等中间件领域的面试官," +
            "您要对面试者问好并进行面试(以上为面试者的简历,请围绕简历内容提问)请结合上下文对话,可以对面试者进行追问.面试者会回答您的问题,您需要根据面试者的回答给出评价," +
            "并给出下一轮的问题,直到面试结束,面试结束时返回你的总体评价,面试结束的标志是面试者回答“面试结束(要求,纯文本格式输出)" +
            "如果面试者超过三次回答不相关内容,直接停止面试";

    private static String MutiUpdateInitSystemPrompt = "您是一为Java,包含Redis,MySQL,MQ等中间件领域的简历优化师," +
            "您要对面试者的简历进行优化(以上为面试者的简历,请围绕简历内容分析)请结合上下文对话,以(问题:改进方案)的方式按顺序回答,注意分段." +
            "并返回你的总体评价,结束的标志是面试者回答“优化结束(要求,纯文本格式输出)" +
            "如果面试者超过三次回答不相关内容,直接停止";

    /**
     * AI大模型
     */
    @Resource
    private GenerationParam generationParam;

    @Resource
    private RagManager ragManager;

    @Resource
    MultiModalConversationParam multiModalConversationResult;
    /**
     * 模拟面试
     * @param interviewrequest
     * @param userid
     * @return
     */
    @Override
    public String interview(interViewRequest interviewrequest, Long userid) {
        //1.从缓存获取模拟面试
        String interviewId = interviewrequest.getInterviewId();
        String userPrompt = interviewrequest.getAnswer();
        String systemPrompt = null;
        if (interviewTemplete.containsKey(interviewId)) {
            //2.1继续模拟面试
             systemPrompt = continueInterView(interviewId, userPrompt);
        }else{
            //2.2开始新的模拟面试
            systemPrompt = initInterView(interviewId);
        }
        systemPrompt = formatText(systemPrompt);
        return systemPrompt;
    }

    /**
     * 调用RAG增强模拟面试
     * @param interviewrequest
     * @param userid
     * @return
     */
    @Override
    public String interviewByRAG(interViewRequest interviewrequest, Long userid) {
        //1.从缓存获取模拟面试
        String interviewId = interviewrequest.getInterviewId();
        String userPrompt = interviewrequest.getAnswer();
        String systemPrompt = null;
        if (interviewTemplete.containsKey(interviewId)) {
            //2.1继续模拟面试
            systemPrompt = RagContinueInterView(interviewId, userPrompt);
        }else{
            //2.2开始新的模拟面试
            systemPrompt = RagInitInterView(interviewId);
        }
        systemPrompt = formatText(systemPrompt);
        return systemPrompt;
    }

    /**
     * 基于简历模拟面试
     * @param interviewrequest
     * @param userid
     * @return
     */
    @Override
    public String interviewByMuti(interViewMutiRequest interviewrequest, Long userid) {
        String interviewId = interviewrequest.getInterviewId();
        String userPrompt = interviewrequest.getAnswer();
        String systemPrompt = null;
        if (interviewTemplete.containsKey(interviewId)) {
            //2.1继续模拟面试
            systemPrompt = continueInterView(interviewId, userPrompt);
        }else{
            //2.2开始新的模拟面试
            systemPrompt = mutiInitInterView(interviewrequest, interviewId);
        }
        systemPrompt = formatText(systemPrompt);
        return systemPrompt;
    }

    /**
     * 简历优化
     * @param interviewrequest
     * @param userid
     * @return
     */
    @Override
    public String interviewUpdateByPNG(interViewMutiRequest interviewrequest, Long userid){
        String interviewId = interviewrequest.getInterviewId();
        String userPrompt = interviewrequest.getAnswer();
        String systemPrompt = null;
        if (interviewTemplete.containsKey(interviewId)) {
            //2.1继续模拟面试
            systemPrompt = continueInterView(interviewId, userPrompt);
        }else{
            //2.2开始新的模拟面试
            systemPrompt = mutiInitInterViewUpdate(interviewrequest,interviewId);
        }
        systemPrompt = formatText(systemPrompt);
        return systemPrompt;
    }

    /**
     * 继续模拟面试
     * @param userPrompt
     * @return
     */
    public String continueInterView(String interviewId,String userPrompt){
        List<Message> interViewDTOS = interviewTemplete.get(interviewId);
        Message message = createMessage(Role.USER, userPrompt);
        interViewDTOS.add(message);
        generationParam.setMessages(interViewDTOS);
        GenerationResult generationResult = callGenerationWithMessages(generationParam);
        Message systemPrompt = generationResult.getOutput().getChoices().get(0).getMessage();
        interViewDTOS.add(systemPrompt);
        return systemPrompt.getContent();
    }

    /**
     * 初始化模拟面试
     * @param interviewId
     * @return
     */
    public String initInterView(String interviewId){
        ArrayList<Message> interViewDTOS = new ArrayList<>();
        Message InitMessage = createMessage(Role.ASSISTANT, InitSystemPrompt);
        interViewDTOS.add(InitMessage);
        generationParam.setMessages(interViewDTOS);
        GenerationResult generationResult = callGenerationWithMessages(generationParam);
        Message systemPrompt = generationResult.getOutput().getChoices().get(0).getMessage();
        interViewDTOS.add(systemPrompt);
        interviewTemplete.put(interviewId,interViewDTOS);
        return systemPrompt.getContent();
    }

    /**
     * 基于RAG的初始化模拟面试
     * @param interviewId
     * @return
     */
    public String RagInitInterView(String interviewId){
        ArrayList<Message> interViewDTOS = new ArrayList<>();
        Message InitMessage = createMessage(Role.ASSISTANT, RAGInitSystemPrompt);
        interViewDTOS.add(InitMessage);
        generationParam.setMessages(interViewDTOS);
        String answer = ragManager.searchQuestionWithContext(RAGInitSystemPrompt,true);
        Message systemPrompt = Message.builder().role(Role.ASSISTANT.getValue()).content(answer).build();
        interViewDTOS.add(systemPrompt);
        interviewTemplete.put(interviewId,interViewDTOS);
        return systemPrompt.getContent();
    }

    /**
     * 基于RAG的继续模拟面试
     * @param interviewId
     * @param userPrompt
     * @return
     */
    public String RagContinueInterView(String interviewId,String userPrompt){
        //1.调用AI(多轮对话)
        List<Message> interViewDTOS = interviewTemplete.get(interviewId);
        Message message = createMessage(Role.USER, userPrompt);
        interViewDTOS.add(message);
        generationParam.setMessages(interViewDTOS);
        GenerationResult generationResult = callGenerationWithMessages(generationParam);
        Message systemPrompt = generationResult.getOutput().getChoices().get(0).getMessage();
        //2.将生成结果RAG自检增强
        String content = systemPrompt.getContent();
        //3.更新AI结果存储到上下文
        systemPrompt.setContent(content);
        interViewDTOS.add(systemPrompt);
        return content;
    }

    /**
     * 基于多模态的初始化简历面试
     * @param interViewMutiRequest
     * @param interviewId
     * @return
     */
    public String mutiInitInterView(interViewMutiRequest interViewMutiRequest,String interviewId){
        String answer = mutiToTest(interViewMutiRequest.getResumeImage());
        ArrayList<Message> interViewDTOS = new ArrayList<>();
        Message InitMessage = createMessage(Role.ASSISTANT, answer);
        interViewDTOS.add(InitMessage);
        generationParam.setMessages(interViewDTOS);
        GenerationResult generationResult = callGenerationWithMessages(generationParam);
        Message systemPrompt = generationResult.getOutput().getChoices().get(0).getMessage();
        interViewDTOS.add(systemPrompt);
        interviewTemplete.put(interviewId,interViewDTOS);
        return systemPrompt.getContent();
    }
    /**
     * 基于多模态的初始化简历优化
     * @param interViewMutiRequest
     * @param interviewId
     * @return
     */
    public String mutiInitInterViewUpdate(interViewMutiRequest interViewMutiRequest,String interviewId){
        String answer = mutiToUpdate(interViewMutiRequest.getResumeImage());
        ArrayList<Message> interViewDTOS = new ArrayList<>();
        Message InitMessage = createMessage(Role.ASSISTANT, answer);
        interViewDTOS.add(InitMessage);
        generationParam.setMessages(interViewDTOS);
        GenerationResult generationResult = callGenerationWithMessages(generationParam);
        Message systemPrompt = generationResult.getOutput().getChoices().get(0).getMessage();
        interViewDTOS.add(systemPrompt);
        interviewTemplete.put(interviewId,interViewDTOS);
        return systemPrompt.getContent();
    }

    /**
     * 生成Message
     * @param role
     * @param content
     * @return
     */
    private static Message createMessage(Role role, String content) {
        return Message.builder().role(role.getValue()).content(content).build();
    }

    /**
     * 调用大模型
     * @param param
     * @return
     * @throws ApiException
     * @throws NoApiKeyException
     * @throws InputRequiredException
     */
    public static GenerationResult callGenerationWithMessages(GenerationParam param) {
        try {
            Generation gen = new Generation();
            return gen.call(param);
        }catch (ApiException e) {
            log.error("调用大模型失败", e);
            ThrowUtil.throwIf(true, ErrorCode.SYSTEM_ERROR, "面试官跑路了,正在找回");
        }catch ( NoApiKeyException e){
            log.error("调用大模型失败", e);
            ThrowUtil.throwIf(true, ErrorCode.SYSTEM_ERROR, "面试官跑路了,正在找回");
        }catch ( InputRequiredException e){
            log.error("调用大模型失败", e);
            ThrowUtil.throwIf(true, ErrorCode.SYSTEM_ERROR, "面试官跑路了,正在找回");
        }
        return null;
    }

    /**
     * AI回答格式优化
     * @param text
     * @return
     */
    public static String formatText(String text) {
        // 步骤1：移除所有特殊标记符号
        String step1 = text.replaceAll("#{3,}|[*]{1,2}|-\\s+", "")
                .replaceAll("\\（等待面试者回答\\）", "");

        // 步骤2：分割段落并过滤空行
        String[] paragraphs = step1.split("\\n+");

        // 步骤3：合并连续空格并重新拼接
        return Arrays.stream(paragraphs)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    public String mutiToTest(String recource){
        Map<String, Object> map = new HashMap<>();
        map.put("image", recource);
        map.put("max_pixels", "6422528");
        map.put("min_pixels", "3136");
        map.put("enable_rotate", true);
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        map,
                        Collections.singletonMap("text", "请提取图像中的项目经历,实习经历,个人技能,专业能力等信息(如果存在)。" +
                                "要求准确无误的提取上述关键信息、不要遗漏和捏造虚假信息，模糊或者强光遮挡的单个文字可以用英文问号?代替。" +
                                "返回数据格式以json方式输出"))).build();
        multiModalConversationResult.setMessages(Collections.singletonList(userMessage));
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationResult result = null;
        try {
            result = conv.call(multiModalConversationResult);
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        } catch (UploadFileException e) {
            throw new RuntimeException(e);
        }
        return (result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text")
                +MutiInitSystemPrompt).toString();
    }

    public String mutiToUpdate(String recource){
        Map<String, Object> map = new HashMap<>();
        map.put("image", recource);
        map.put("max_pixels", "6422528");
        map.put("min_pixels", "3136");
        map.put("enable_rotate", true);
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        map,
                        Collections.singletonMap("text", "请提取图像中的项目经历,实习经历,个人技能,专业能力等信息(如果存在)。" +
                                "要求准确无误的提取上述关键信息、不要遗漏和捏造虚假信息，模糊或者强光遮挡的单个文字可以用英文问号?代替。" +
                                "返回数据格式以json方式输出"))).build();
        multiModalConversationResult.setMessages(Collections.singletonList(userMessage));
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationResult result = null;
        try {
            result = conv.call(multiModalConversationResult);
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        } catch (UploadFileException e) {
            throw new RuntimeException(e);
        }
        return (result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text")
                +MutiUpdateInitSystemPrompt).toString();
    }
}

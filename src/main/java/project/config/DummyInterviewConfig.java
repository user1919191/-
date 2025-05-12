package project.config;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

@Component
public class DummyInterviewConfig {

    @Resource
    private AiConfig aiConfig;

    /**
     * 普通AI配置
     */
    @Value("${spring.ai.dashscope.api-key.key1}")
    private String apiKey;

    @Value("${spring.ai.dashscope.chat.options.model.model1}")
    private String modelName;

    /**
     * 多模态AI配置
     * @return
     */
    @Value("${spring.ai.dashscope.api-key.key2}")
    private String multiModelApiKey;

    @Value("${spring.ai.dashscope.chat.options.model.model2}")
    private String multiModelModelName;

    /**
     * RAG配置
     */
    @Value("${langchain4j.community.dashscope.api-key}")
    private String ragApiKey;

    @Value("${langchain4j.community.dashscope.chat-model.model-name}")
    private String ragModelName;

    @Value("${langchain4j.community.dashscope.embedding-model.model-name}")
    private String embbingModelName;


    /**
     * 普通AI调用接口
     * @return
     */
    @Bean
    public GenerationParam generationParam() {
        return GenerationParam.builder()
                // 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(apiKey)
                // 模型列表：https://help.aliyun.com/zh/model-studio/getting-started/models
                .model(modelName)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
    }

    /**
     * 多模态AI调用接口
     * @return
     */
    @Bean
    public MultiModalConversationParam multiModalConversationResult(){
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(multiModelApiKey)
                .model(multiModelModelName)
                .build();
        return param;
    }

    /**
     * RAG配置
     * @return
     */
    @Bean
    public ChatLanguageModel dashScopeChatModel() {
        return QwenChatModel.builder()
                .apiKey(ragApiKey)
                .modelName(ragModelName)
                .temperature(0.3F)
                .topP(0.9)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(ragApiKey)
                .modelName(embbingModelName)
                .build();
    }
}

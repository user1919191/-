package project.manager;

import cn.hutool.core.collection.CollUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import project.common.ErrorCode;
import project.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiManager {

    private static final String DEFAULT_MODEL = "deepseek-v3-250324";

    private final ObjectProvider<ArkService> aiServiceProvider;

    public AiManager(ObjectProvider<ArkService> aiServiceProvider) {
        this.aiServiceProvider = aiServiceProvider;
    }

    public String doChat(String userPrompt) {
        return doChat("", userPrompt, DEFAULT_MODEL);
    }

    public String doChat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, DEFAULT_MODEL);
    }

    public String doChat(String systemPrompt, String userPrompt, String model) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build());
        messages.add(ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build());
        return doChat(messages, model);
    }

    public String doChat(List<ChatMessage> messages) {
        return doChat(messages, DEFAULT_MODEL);
    }

    public String doChat(List<ChatMessage> messages, String model) {
        ArkService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI service is not configured");
        }
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .build();
        List<ChatCompletionChoice> choices = aiService.createChatCompletion(chatCompletionRequest).getChoices();
        if (CollUtil.isNotEmpty(choices)) {
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI service returned no result");
    }
}

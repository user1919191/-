package project.config;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class DummyInterviewConfig {

    @Value("${langchain4j.community.dashscope.api-key}")
    private String ragApiKey;

    @Value("${langchain4j.community.dashscope.embedding-model.model-name}")
    private String embbingModelName;

    @Bean
    public EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(ragApiKey)
                .modelName(embbingModelName)
                .build();
    }
}

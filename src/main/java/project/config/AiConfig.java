package project.config;

import com.volcengine.ark.runtime.service.ArkService;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AiConfig {

    private String apiKey;

    @Bean
    @Lazy
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${ai.apiKey:}')")
    public ArkService aiService() {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        return ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .apiKey(apiKey)
                .build();
    }
}

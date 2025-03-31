package project.config;

import com.volcengine.ark.runtime.service.ArkService;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Ai配置类
 */

@Configuration
@Data
@ConfigurationProperties(prefix = "ai")
public class AIconfig {
    /**
     * ApiKey
     */
    private String apikey;
    /**
     * 请求路径
     */
    private String url;

    /**
     * Ai请求客户端
     */
    public ArkService AIServer()
    {
        ConnectionPool connectionPool = new ConnectionPool(5,1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).baseUrl(url).apiKey(apikey).build();
        return service;
    }
}

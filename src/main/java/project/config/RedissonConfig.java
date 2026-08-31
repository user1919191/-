package project.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.redisson.config.Config;

import java.lang.reflect.Proxy;

/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * Redisson配置类
 */

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig {
    @Value("${app.redisson.enabled:false}")
    private boolean redissonEnabled;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private int port;

    /**
     * 数据库
     */
    private Integer database;

    /**
     * 密码
     */
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        if (!redissonEnabled) {
            return disabledClient(null);
        }
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer().setAddress("redis://" + host + ":" + port)
                .setDatabase(database).setPassword(password);
        try {
            return Redisson.create(config);
        } catch (Exception e) {
            return disabledClient(e);
        }
    }

    private RedissonClient disabledClient(Exception e) {
        return (RedissonClient) Proxy.newProxyInstance(
                RedissonClient.class.getClassLoader(),
                new Class[]{RedissonClient.class},
                (proxy, method, args) -> {
                    if ("shutdown".equals(method.getName())) {
                        return null;
                    }
                    throw new IllegalStateException("Redis is unavailable, Redisson feature is disabled", e);
                }
        );
    }
}

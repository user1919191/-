package project.config;

import com.jd.platform.hotkey.client.ClientStarter;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 热键配置类
 */
@Configuration
@ConfigurationProperties(prefix = "hotkey")
@Data
public class HotKeyConfig {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 缓存大小
     */
    private Integer caffeineSize;

    /**
     * 推送周期
     */
    private long pushPeriod;

    /**
     * etcd 服务器地址
     */
    private String etcdServer;

    @Bean
    public void InitHotKey() {
        ClientStarter.Builder builder = new ClientStarter.Builder();
        ClientStarter HotKeyBuild = builder.setAppName(appName).setCaffeineSize(caffeineSize).setPushPeriod(pushPeriod)
                .setEtcdServer(etcdServer).build();
        HotKeyBuild.startPipeline();
    }
}

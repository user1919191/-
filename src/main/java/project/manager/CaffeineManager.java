package project.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import project.model.vo.QuestionVO;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeineManager {

    /**
     * Cache集合
     */
    private final ConcurrentHashMap<String,Cache<String,Object>> cacheMap = new ConcurrentHashMap<>();

    /**
     *题目本地缓存
     */
    @Bean(name = "questionCache")
    public Cache<String,Object> questionCache(){
        return cacheMap.computeIfAbsent("questionCache", k->{
            return Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterAccess(10, TimeUnit.MINUTES).build();
        });
    }

    /**
     * 用户本地缓存
     */
    @Bean(name = "userCache")
    public Cache<String, Object> userCache() {
        return cacheMap.computeIfAbsent("userCache", key ->
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterAccess(5, TimeUnit.MINUTES).build());
    }
    //Todo 缺少缓存预热和CHM的集成
}

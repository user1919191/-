package project.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.model.entity.Question;
import project.model.entity.User;
import project.model.vo.QuestionVO;
import project.model.vo.UserVO;
import project.service.Imp.QuestionServiceImp;
import project.service.Imp.UserServiceImp;
import project.service.QuestionService;
import project.service.UserService;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@EnableAspectJAutoProxy(exposeProxy = true)
public class CaffeineManager implements InitializingBean, ApplicationContextAware {

    /**
     * Cache相关配置
     */
    private final int QUESTION_CACHE_SIZE = 1000;
    private final int USER_CACHE_SIZE = 500;
    private final int QUESTION_CACHE_EXPIRE = 10;
    private final int USER_CACHE_EXPIRE = 30;


    /**
     * 上下文对象获取
     */
    private ApplicationContext applicationContext;

    /**
     * Cache集合
     */
    private final ConcurrentHashMap<String,Cache<String,Object>> cacheMap = new ConcurrentHashMap<>();

    /**
     *题目本地缓存
     */
    @Bean(name = "questionCache")
    public Cache<String,Object> questionCache(){
        return  cacheMap.computeIfAbsent("questionCache", key ->
                Caffeine.newBuilder().recordStats()
                        .maximumSize(USER_CACHE_SIZE)
                        .expireAfterAccess(USER_CACHE_EXPIRE, TimeUnit.MINUTES).build());
    }

    /**
     * 用户本地缓存
     */
    @Bean(name = "userCache")
    public Cache<String, Object> userCache() {
        return  cacheMap.computeIfAbsent("UserCache", k->
                Caffeine.newBuilder().recordStats()
                        .maximumSize(QUESTION_CACHE_SIZE)
                        .expireAfterAccess(QUESTION_CACHE_EXPIRE, TimeUnit.MINUTES).build());
    }

    /**
     * 缓存预热
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        CompletableFuture.runAsync(this::warmUpQuestionCache);
        CompletableFuture.runAsync(this::warmUpUserCache);
    }

    /**
     * 题目缓存预热
     */
    private void warmUpQuestionCache() {
        Cache<String, Object> questionCache = cacheMap.get("questionCache");
        QuestionService questionServiceImp = applicationContext.getBean(QuestionService.class);
        LambdaQueryWrapper<Question> queryWrapper = Wrappers.lambdaQuery(Question.class).select(Question::getId);
        List<Question> questionList = questionServiceImp.list(queryWrapper);
        questionList.forEach(question -> {
            if(JdHotKeyStore.isHotKey(question.getId().toString())){
                questionCache.put(question.getId().toString(),question);
            }
        });
    }

    /**
     * 用户缓存预热
     */
    private void warmUpUserCache() {
        Cache<String, Object> userCache = cacheMap.get("userCache");
        UserService userServiceImp = applicationContext.getBean(UserService.class);
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery(User.class).last("limit 350");
        List<UserVO> userVOList = userServiceImp.getUserVOList(userServiceImp.list(queryWrapper));
        userVOList.forEach(userVO -> {
            userCache.put(userVO.getId().toString(),userVO);
        });
    }

    /**
     * 定时任务检测Caffeine
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void printCaffeineStats(){
        //1.获取对应Caffeine和指标
        Cache<String, Object> questionCache = cacheMap.get("questionCache");
        Cache<String, Object> userCache = cacheMap.get("UserCache");
        CacheStats questionStats = questionCache.stats();
        CacheStats userStats = userCache.stats();
        double questionHitRate = questionStats.hitRate();
        log.info("定期清理前-->QuestionCaffeine状态监控:questionCache hitRate:{},missRate:{},evictionCount:{}"
                ,questionHitRate,questionStats.missRate(),questionStats.evictionCount());
        log.info("定期清理前-->UserCaffeine状态监控:userCache hitRate:{},missRate:{},evictionCount:{}"
                ,userStats.hitRate(),userStats.missRate(),userStats.evictionCount());
        //2.针对QuestionCaffeine实现状态修复
        //2.1针对命中率低重新缓存预热
        if(questionHitRate <= 0.5){
            warmUpQuestionCache();
        }else{
            //2.2针对容量即将达到上限的自动清理
            long questionEstimatedSize = questionCache.estimatedSize();
            if(questionEstimatedSize >= QUESTION_CACHE_SIZE*0.9){
                log.warn("questionCache容量即将达到上限的,当前为{},已启动自动清理", questionEstimatedSize);
                questionCache.cleanUp();
            }
            //2.3如果容量仍旧即将达到上限,启用主动清理
            if(questionEstimatedSize>= QUESTION_CACHE_SIZE*0.9){
                //2.4动态最小淘汰数量设置
                int itemsToEvict = Math.max(10, (int) (questionEstimatedSize * 0.1));
                questionCache.policy().eviction().ifPresent(eviction->{
                    Set<@NonNull String> stringSet = eviction.coldest(itemsToEvict).keySet();
                    questionCache.invalidateAll(stringSet);
                });
            }
        }

        //3.针对UserCaffeine实现状态修复
        //3.1针对命中率低重新缓存预热
        if(userStats.hitRate() <= 0.5){
            warmUpUserCache();
        }else{
        //3.2针对容量即将达到上限的自动清理
            if(userCache.estimatedSize() >= USER_CACHE_SIZE*0.9){
                log.warn("userCache容量即将达到上限的,当前为{},已启动自动清理",userCache.estimatedSize());
                userCache.cleanUp();
            }
            //3.3如果容量仍旧即将达到上限,启用主动清理
            if(userCache.estimatedSize() >= USER_CACHE_SIZE*0.9){
                int itemsToEvict = Math.max(10, (int) (userCache.estimatedSize() * 0.1));
                userCache.policy().eviction().ifPresent(eviction->{
                    Set<@NonNull String> stringSet = eviction.coldest(itemsToEvict).keySet();
                    userCache.invalidateAll(stringSet);
                });
            }
        }
        //记录监控日志
        log.info("定期清理后-->QuestionCaffeine状态监控:questionCache hitRate:{},missRate:{},evictionCount:{}"
                ,questionHitRate,questionStats.missRate(),questionStats.evictionCount());
        log.info("定期清理后-->UserCaffeine状态监控:userCache hitRate:{},missRate:{},evictionCount:{}"
                ,userStats.hitRate(),userStats.missRate(),userStats.evictionCount());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

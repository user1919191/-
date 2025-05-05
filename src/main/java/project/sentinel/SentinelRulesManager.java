package project.sentinel;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * Sentinel流量控制熔断控制器
 */

@Component
public class SentinelRulesManager {

    @PostConstruct
    public void initRules() throws Exception {
        initFlowRules();
        initDegradeRules();
        ListenRules();
    }

    /**
     * 流量控制Manager
     */
    public void initFlowRules()
    {
        ParamFlowRule paramFlowRule = new ParamFlowRule(SentinelConstant.ListQuestionVoByPage)
                .setParamIdx(0).setCount(60).setDurationInSec(60);
        ParamFlowRuleManager.loadRules(Collections.singletonList(paramFlowRule));
    }

    /**
     * 流量熔断Manager
     */
    public void initDegradeRules()
    {
        DegradeRule degradeRule = new DegradeRule(SentinelConstant.ListQuestionVoByPage)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType()).setCount(0.2)
                .setTimeWindow(60).setStatIntervalMs(30 * 1000).setMinRequestAmount(5).setSlowRatioThreshold(3);
        DegradeRuleManager.loadRules(Collections.singletonList(degradeRule));
    }

    /**
     * 持久化为本地文件
     * @throws Exception
     */
    public void ListenRules() throws Exception{
        try {
            // 1. 创建规则容器
            Map<String, Object> rules = new HashMap<>(2);

            // 2. 获取当前规则
            List<?> flowRules = ParamFlowRuleManager.getRules();
            List<?> degradeRules = DegradeRuleManager.getRules();

            // 3. 结构化存储
            rules.put("flowRules", flowRules);
            rules.put("degradeRules", degradeRules);

            // 4. 序列化为 JSON
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);

            // 5. 定义存储路径
            String dirPath = "./sentinel-rules";
            String filePath = dirPath + "/sentinel-rules.json";

            // 6. 确保目录存在
            File directory = new File(dirPath);
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (!created) {
                    throw new IOException("无法创建目录: " + dirPath);
                }
            }

            // 7. 写入文件（自动关闭资源）
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(json);
                System.out.println("规则已持久化至: " + new File(filePath).getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("规则持久化失败: ");
            e.printStackTrace();
            throw new RuntimeException("规则持久化失败", e);
        }
    }
}

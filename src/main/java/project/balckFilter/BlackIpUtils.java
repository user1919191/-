package project.balckFilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.yaml.snakeyaml.Yaml;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.collection.CollUtil;
import project.exception.ThrowUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 黑名单过滤工具类
 */

@Slf4j
public class BlackIpUtils {

    private static BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(100);

    /**
     * 判断Ip是否在
     */
    public static boolean isBlack(String ip){
        return bitMapBloomFilter.contains(ip);
    }

    /**
     * 重建黑名单
     * @param configInfo
     */
    public static void reBuildBlackIp(String configInfo)
    {
        if (StrUtil.isBlank(configInfo)) {
            log.warn("Empty config info");
            synchronized (BlackIpUtils.class) {
                bitMapBloomFilter = new BitMapBloomFilter(100);
            }
            return;
        }
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(configInfo);
            if (map == null) {
                log.warn("YAML parse result is null");
                return;
            }
            List<String> blackIpList = (List<String>) map.get("blackIpList");
            if (CollUtil.isEmpty(blackIpList)) {
                log.info("No black IPs configured, resetting filter");
                blackIpList = Collections.emptyList();
            }
            // 动态调整布隆过滤器大小
            int expectedSize = Math.max(blackIpList.size() * 2, 100);
            BitMapBloomFilter newFilter = new BitMapBloomFilter(expectedSize);

            for (String ip : blackIpList) {
                newFilter.add(ip);
            }
            synchronized (BlackIpUtils.class) {
                bitMapBloomFilter = newFilter;
                log.info("Blacklist rebuilt successfully. Size: {}", blackIpList.size());
            }
        } catch (Exception e) {
            log.error("Failed to rebuild blacklist", e);
        }
    }

    /**
     * 添加到黑名单
     */
    public static boolean addBlackIp(String ip){
        return bitMapBloomFilter.add(ip);
    }
}

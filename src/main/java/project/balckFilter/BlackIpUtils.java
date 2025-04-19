package project.balckFilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.collection.CollUtil;
import java.util.List;
import java.util.Map;

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
        if(StrUtil.isBlank(configInfo)) configInfo = "";
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(configInfo, Map.class);
        List<String> balckIpList = (List<String>) map.get("balckIpList");
        synchronized (BlackIpUtils.class){
            if(CollUtil.isNotEmpty(balckIpList)){
                BitMapBloomFilter bloomFilter = new BitMapBloomFilter(958506);
                for(String ip : balckIpList){
                    bloomFilter.add(ip);
                }
            }else {
                bitMapBloomFilter = new BitMapBloomFilter(100);
            }
        }
    }

    /**
     * 添加到黑名单
     */
    public boolean addBlackIp(String ip){
        return bitMapBloomFilter.add(ip);
    }
}

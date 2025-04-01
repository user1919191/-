package project.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import org.springframework.stereotype.Component;
import project.config.CorsConfig;
import project.config.CosClientConfig;

import javax.annotation.Resource;
import java.io.File;

/**
 * Cos文件上传管理器
 */

@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     * @param key
     * @param file
     * @return
     */
    public PutObjectRequest putObjectRequest(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return putObjectRequest;
    }
}

package project.model.entity;

import lombok.Data;

import java.util.Date;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 模拟面试实体类
 */
@Data
public class InterView {

    /**
     * 模拟面试id
     */
    private String interViewId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 压缩数据
     */
    private String compressData;

    /**
     * 面试时间
     */
    private Date date;
}

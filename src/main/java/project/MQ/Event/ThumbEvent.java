package project.MQ.Event;

import cn.hutool.core.lang.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 点赞事件
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbEvent implements Serializable {

    private int id = UUID.fastUUID().hashCode();

    /**
     * 用户ID
     */
    private Long userid;

    /**
     * 题目ID
     */
    private Long postId;

    /**
     * 点赞事件类型
     */
    private ThumbType isThumb;

    /**
     * 点赞时间
     */
    private LocalDateTime dateTime;

    /**
     * 重试次数
     */
    private int retryCount = 0;

    /**
     * 点赞类型枚举
     */
    public enum ThumbType {
        /**
         * 点赞
         */
        THUMB,
        /**
         * 取消点赞
         */
        CANCEL_THUMB
    }

    /**
     * 本消息完成状态
     */
    private int status = 0;
}

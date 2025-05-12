package project.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * RocketMQ 点赞消息实体类
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class thumbEvent implements Serializable {

    /**
     * 点赞用户ID
     */
    private Long userId;

    /**
     * 点赞题目ID
     */
    private Long questionId;

    /**
     * 点赞事件类型
     */
    private EventType isThumb;

    /**
     * 点赞时间
     */
    private LocalDateTime thumbTime;

    /**
     * 点赞事件枚举
     */
    public enum EventType {
        /**
         * 点赞
         */
        THUMB,

        /**
         * 取消点赞
         */
        CANCEL_THUMB
    }
}

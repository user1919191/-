package project.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

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

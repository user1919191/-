package project.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 重试点赞实体类
 */
@Data
@AllArgsConstructor
public class RetryThumb {

    /**
     * 主键id
     */

    private String id;

    /**
     * 点赞帖子id
     */
    private Long postId;

    /**
     * 用户id
     */
    private User user;

}

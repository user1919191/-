package project.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

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

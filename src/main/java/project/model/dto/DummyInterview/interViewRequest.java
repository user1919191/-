package project.model.dto.DummyInterview;

import lombok.Data;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 面试请求体
 */
@Data
public class interViewRequest {

    /**
     * 模拟面试序列号(由前端分配,判断是不是同一场模拟面试)
     */
    private String interviewId;

    /**
     * 面试者回答
     */
    private String answer;
}

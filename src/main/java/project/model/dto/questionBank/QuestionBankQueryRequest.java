package project.model.dto.questionBank;

import lombok.Data;
import lombok.EqualsAndHashCode;
import project.common.PageRequest;

import java.io.Serializable;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * 查询题库请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionBankQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * id
     */
    private Long notId;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 图片
     */
    private String picture;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 是否要关联查询题目列表
     */
    private boolean needQueryQuestionList;

    private static final long serialVersionUID = 1L;
}
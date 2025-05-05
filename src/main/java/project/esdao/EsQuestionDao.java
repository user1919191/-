package project.esdao;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import project.model.dto.question.QuestionEsDTO;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * Es 查询问题操作
 */
public interface EsQuestionDao extends ElasticsearchRepository<QuestionEsDTO,Long> {
    public QuestionEsDTO findByUserId(Long userId);
}

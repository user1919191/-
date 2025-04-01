package project.esdao;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import project.model.dto.question.QuestionEsDTO;

/**
 * Es 查询问题操作
 */
public interface EsQuestionDao extends ElasticsearchRepository<QuestionEsDTO,Long> {
    public QuestionEsDTO findByUserId(Long userId);
}

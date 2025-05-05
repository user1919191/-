package project.esdao;


import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import project.model.dto.post.PostEsDTO;

import java.util.List;
/**
 * @author 我要大声哈哈哈哈(user1919191)
 * @Profieession https://github.com/user1919191
 */

/**
 * ES查询帖子操作
 */
public interface EsPostDao extends ElasticsearchRepository<PostEsDTO, Long>{
    List<PostEsDTO> findByUserId(Long userId);
}

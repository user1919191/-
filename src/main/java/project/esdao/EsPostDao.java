package project.esdao;


import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import project.model.dto.post.PostEsDTO;

import java.util.List;

/**
 * ES查询帖子操作
 */
public interface EsPostDao extends ElasticsearchRepository<PostEsDTO, Long>{
    List<PostEsDTO> findByUserId(Long userId);
}

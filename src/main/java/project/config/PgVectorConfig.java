package project.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PgVectorConfig {

    @Value("${spring.datasource.pgvector.username}")
    private String userAccount;

    @Value("${spring.datasource.pgvector.password}")
    private String userPassword;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(){
        return PgVectorEmbeddingStore.builder()
                .host("rm-cn-2bl49c1dp000qgpo.rwlb.rds.aliyuncs.com")
                .port(5432)
                .database("Project_1")
                .user(userAccount)
                .password(userPassword)
                .table("embeddings")
                .dimension(1536)
                .build();
    }
}
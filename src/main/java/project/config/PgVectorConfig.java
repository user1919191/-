package project.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "app.pgvector", name = "enabled", havingValue = "true")
public class PgVectorConfig {

    @Value("${spring.datasource.pgvector.username}")
    private String userAccount;

    @Value("${spring.datasource.pgvector.password}")
    private String userPassword;

    @Value("${spring.datasource.pgvector.url:}")
    private String jdbcUrl;

    @Value("${app.pgvector.host:rm-cn-2bl49c1dp000qgpo.rwlb.rds.aliyuncs.com}")
    private String host;

    @Value("${app.pgvector.port:5432}")
    private int port;

    @Value("${app.pgvector.database:Project_1}")
    private String database;

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl == null || jdbcUrl.isBlank()
                ? "jdbc:postgresql://%s:%d/%s".formatted(host, port, database)
                : jdbcUrl);
        dataSource.setUsername(userAccount);
        dataSource.setPassword(userPassword);
        return dataSource;
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(){
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(userAccount)
                .password(userPassword)
                .table("embeddings")
                .dimension(1536)
                .build();
    }
}

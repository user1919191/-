package project.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Langchain4j-MarkDown-RAG 文档加载器
 */
@Component
@Slf4j
public class AiRagConfig {

    private final ResourcePatternResolver resourceResolver;

    public AiRagConfig(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public List<Document> loadMarkdownDocuments() throws IOException {
        Resource[] resources = resourceResolver.getResources("classpath:documents/**/*.md");
        return FileSystemDocumentLoader.loadDocumentsRecursively(Arrays.toString(resources));
    }

    // 内容检索器
    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel dashScopeEmbeddingModel
    ) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(dashScopeEmbeddingModel)
                .maxResults(5)
                .dynamicMinScore(query -> 0.6)
                .build();
    }
}

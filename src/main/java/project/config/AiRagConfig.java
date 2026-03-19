package project.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import project.common.ErrorCode;
import project.exception.ThrowUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class AiRagConfig {

    private final ResourcePatternResolver resourceResolver;

    @Value("${app.interview.rag.document-pattern:classpath:document/**/*.md}")
    private String ragDocumentPattern;

    @Value("${app.interview.rag.max-results:5}")
    private int maxResults;

    @Value("${app.interview.rag.min-score:0.6}")
    private double minScore;

    @Value("${app.interview.rag.ingest-on-startup:false}")
    private boolean ingestOnStartup;

    @Value("${app.interview.rag.splitter-max-segment-size:800}")
    private int splitterMaxSegmentSize;

    @Value("${app.interview.rag.splitter-max-overlap-size:120}")
    private int splitterMaxOverlapSize;

    public AiRagConfig(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public List<Document> loadMarkdownDocuments() {
        try {
            Resource[] resources = resourceResolver.getResources(ragDocumentPattern);
            List<Document> documents = new ArrayList<>();
            TextDocumentParser parser = new TextDocumentParser(StandardCharsets.UTF_8);
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }
                try (InputStream inputStream = resource.getInputStream()) {
                    Document parsed = parser.parse(inputStream);
                    Metadata metadata = Metadata.from(Document.FILE_NAME, resource.getFilename());
                    documents.add(Document.from(parsed.text(), metadata));
                }
            }
            log.info("Loaded {} RAG markdown documents from pattern {}", documents.size(), ragDocumentPattern);
            return documents;
        } catch (IOException e) {
            log.error("Failed to load RAG markdown documents from pattern {}", ragDocumentPattern, e);
            ThrowUtil.throwIf(true, ErrorCode.SYSTEM_ERROR, "Failed to load RAG markdown documents");
            return List.of();
        }
    }

    @Bean
    public ApplicationRunner ragDocumentVerifier(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel
    ) {
        return args -> {
            List<Document> documents = loadMarkdownDocuments();
            log.info("RAG startup check completed, {} documents are available", documents.size());
            if (!ingestOnStartup || documents.isEmpty()) {
                return;
            }
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(new DocumentByParagraphSplitter(splitterMaxSegmentSize, splitterMaxOverlapSize))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(documents);
            log.info("RAG startup ingestion completed for {} documents", documents.size());
        };
    }

    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel
    ) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .dynamicMinScore(query -> minScore)
                .build();
    }
}

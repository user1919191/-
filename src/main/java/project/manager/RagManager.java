package project.manager;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import project.model.dto.rag.RagEvidence;
import project.model.dto.rag.RagRetrievalResult;
import project.service.rag.ParentChildRagService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RagManager {

    private final ContentRetriever contentRetriever;
    private final ParentChildRagService parentChildRagService;

    public RagManager(ObjectProvider<ContentRetriever> contentRetrieverProvider,
                      ObjectProvider<ParentChildRagService> parentChildRagServiceProvider) {
        this.contentRetriever = contentRetrieverProvider.getIfAvailable();
        this.parentChildRagService = parentChildRagServiceProvider.getIfAvailable();
    }

    public String buildContextBlock(String question) {
        return formatForPrompt(retrieveEvidences(question, 1, "NORMAL"));
    }

    public RagRetrievalResult retrieveEvidences(String question) {
        return retrieveEvidences(question, 1, "NORMAL");
    }

    public RagRetrievalResult retrieveEvidences(String question, int attempt, String retrievalStrategy) {
        RagRetrievalResult result = new RagRetrievalResult();
        result.setQuery(question);
        result.setAttempt(attempt);
        result.setRetrievalStrategy(retrievalStrategy);
        if (contentRetriever == null && parentChildRagService == null) {
            result.setEnough(false);
            result.setConfidence(0D);
            result.setPromptContext("RAG is disabled in the current environment.");
            return result;
        }
        List<RagEvidence> evidences = retrieve(question, 5, retrievalStrategy);
        if (evidences.isEmpty()) {
            result.setEnough(false);
            result.setConfidence(0D);
            result.setPromptContext("No relevant knowledge base snippets were retrieved for this round.");
            return result;
        }
        result.setEvidences(evidences);
        result.setEnough(evidences.size() >= 3);
        result.setConfidence(Math.min(1D, evidences.size() / 5D));
        result.setPromptContext(formatForPrompt(result));
        return result;
    }

    public RagRetrievalResult retrieveByQueries(List<String> queries, int attempt, String retrievalStrategy) {
        RagRetrievalResult result = new RagRetrievalResult();
        result.setQuery(queries == null ? "" : String.join(" || ", queries));
        result.setAttempt(attempt);
        result.setRetrievalStrategy(retrievalStrategy);
        if (contentRetriever == null && parentChildRagService == null) {
            result.setEnough(false);
            result.setConfidence(0D);
            result.setPromptContext("RAG is disabled in the current environment.");
            return result;
        }
        Map<String, RagEvidence> deduplicated = new LinkedHashMap<>();
        List<String> safeQueries = queries == null ? List.of() : queries;
        for (String query : safeQueries) {
            String normalizedQuery = StringUtils.trimToEmpty(query);
            if (normalizedQuery.isEmpty()) {
                continue;
            }
            List<RagEvidence> queryEvidences = retrieve(normalizedQuery, 8, retrievalStrategy);
            for (RagEvidence evidence : queryEvidences) {
                String key = StringUtils.trimToEmpty(evidence.getContent());
                if (key.isEmpty()) {
                    continue;
                }
                RagEvidence existing = deduplicated.get(key);
                if (existing == null || scoreOf(evidence) > scoreOf(existing)) {
                    deduplicated.put(key, evidence);
                }
            }
        }
        List<RagEvidence> evidences = new ArrayList<>(deduplicated.values());
        for (int i = 0; i < evidences.size(); i++) {
            RagEvidence evidence = evidences.get(i);
            evidence.setIndex(i + 1);
            if (evidence.getScore() == null || evidence.getScore() <= 0D) {
                evidence.setScore(1D / (i + 1));
            }
        }
        result.setEvidences(evidences);
        result.setEnough(evidences.size() >= 2);
        result.setConfidence(Math.min(1D, evidences.size() / 5D));
        result.setPromptContext(formatForPrompt(result));
        return result;
    }

    public RagRetrievalResult expandRetrieve(String question, int attempt) {
        String expandedQuery = question + "\nAlso retrieve common mistakes, follow-up directions, and answer template hints.";
        return retrieveEvidences(expandedQuery, attempt, attempt == 2 ? "EXPANDED" : "CROSS_TOPIC");
    }

    public String formatForPrompt(RagRetrievalResult result) {
        if (result == null || result.getEvidences() == null || result.getEvidences().isEmpty()) {
            return "No relevant knowledge base snippets were retrieved for this round.";
        }
        return result.getEvidences().stream()
                .map(evidence -> "[" + evidence.getIndex() + "] " + evidence.getContent())
                .collect(Collectors.joining("\n"));
    }

    private List<RagEvidence> mapContentsToEvidences(List<Content> contents,
                                                     String sourceQuery,
                                                     String retrievalStrategy,
                                                     int limit) {
        List<RagEvidence> evidences = new ArrayList<>();
        if (contents == null || contents.isEmpty()) {
            return evidences;
        }
        int index = 1;
        for (Content content : contents) {
            if (content == null || content.textSegment() == null) {
                continue;
            }
            String text = StringUtils.trimToEmpty(content.textSegment().text());
            if (text.isEmpty()) {
                continue;
            }
            RagEvidence evidence = new RagEvidence();
            evidence.setIndex(index++);
            evidence.setContent(text);
            evidence.setSource(content.textSegment().metadata() == null ? "knowledge-base" : String.valueOf(content.textSegment().metadata()));
            evidence.setTopic(retrievalStrategy);
            evidence.setScore(1D / evidence.getIndex());
            evidence.setEvidenceType("STANDARD_POINT");
            evidence.setSourceQuery(sourceQuery);
            evidence.setParentId(extractMetadataValue(content, "parentId"));
            evidence.setChildId(extractMetadataValue(content, "childId"));
            evidence.setSectionTitle(extractMetadataValue(content, "sectionTitle"));
            evidences.add(evidence);
            if (evidences.size() >= limit) {
                break;
            }
        }
        return evidences;
    }

    private String extractMetadataValue(Content content, String key) {
        if (content == null || content.textSegment() == null || content.textSegment().metadata() == null) {
            return "";
        }
        Object value = content.textSegment().metadata().toMap().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private double scoreOf(RagEvidence evidence) {
        return evidence == null || evidence.getScore() == null ? 0D : evidence.getScore();
    }

    private List<RagEvidence> retrieve(String query, int limit, String retrievalStrategy) {
        if (parentChildRagService != null) {
            return parentChildRagService.retrieve(query, limit, retrievalStrategy);
        }
        if (contentRetriever == null) {
            return new ArrayList<>();
        }
        return mapContentsToEvidences(
                contentRetriever.retrieve(Query.from(query)),
                query,
                retrievalStrategy,
                limit
        );
    }
}

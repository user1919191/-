package project.manager;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagManager {

    private final ContentRetriever contentRetriever;

    public RagManager(ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
    }

    public String buildContextBlock(String question) {
        List<Content> contents = contentRetriever.retrieve(Query.from(question));
        if (contents == null || contents.isEmpty()) {
            return "No relevant knowledge base snippets were retrieved for this round.";
        }
        return contents.stream()
                .map(Content::textSegment)
                .map(segment -> segment.text().trim())
                .filter(text -> !text.isEmpty())
                .limit(5)
                .map(text -> "- " + text)
                .collect(Collectors.joining("\n"));
    }
}

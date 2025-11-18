package io.jmix.ai.mcpdocs.service;

import io.jmix.ai.mcpdocs.util.AiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static io.jmix.ai.mcpdocs.api.JmixDocs.*;
import static io.jmix.ai.mcpdocs.util.AiUtil.getRerankResultsAsString;

@Service
public class JmixDocsResearchService {

   private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VectorStore vectorStore;
    private final Reranker reranker;

    public JmixDocsResearchService(VectorStore vectorStore, Reranker reranker) {
        this.vectorStore = vectorStore;
        this.reranker = reranker;
    }

    public List<String> searchForJmixDocs(String query) {
        return executeSearch(query, SIMILARITY_THRESHOLD, TOP_K).lines().toList();
    }

    @SuppressWarnings("ConstantValue")
    protected String executeSearch(String queryText, double similarityThreshold, int topK) {

        SearchRequest searchRequest = SearchRequest.builder()
                .filterExpression(new FilterExpressionBuilder().eq("type", "docs").build())
                .query(queryText)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents == null) {
            logger.info("No documents found for the query");
            return getNoResultsMessage();
        }
        logger.info("Found documents ({}): {}", documents.size(), AiUtil.getDocSourcesAsString(documents));

//        documents = postRetrievalProcessor.process(queryText, documents);
//        if (documents.isEmpty()) {
//            logger.info("All documents filtered out by PostRetrievalProcessor");
//            return getNoResultsMessage();
//        }

        List<Document> filteredDocuments;

        List<Reranker.Result> rerankResults = reranker.rerank(queryText, documents, TOP_RERANKED);

        if (rerankResults == null) {
            logger.info("Reranking failed, filtering by minScore");
            filteredDocuments = documents.stream()
                    .filter(document ->
                            MIN_SCORE <= 0.0 || document.getScore() == null || document.getScore() >= MIN_SCORE)
                    .toList();
            logger.info("Filtered documents ({}): {}", filteredDocuments.size(), AiUtil.getDocSourcesAsString(filteredDocuments));

        } else {
            List<Reranker.Result> filteredRerankResults = rerankResults.stream()
                    .filter(rr -> rr.score() >= MIN_RERANKED_SCORE)
                    .toList();
            logger.info("Reranked documents ({}): {}", filteredRerankResults.size(), getRerankResultsAsString(filteredRerankResults));

            for (Reranker.Result result : filteredRerankResults) {
                result.document().getMetadata().put("rerankScore", result.score());
            }

            filteredDocuments = filteredRerankResults.stream()
                    .map(Reranker.Result::document)
                    .toList();
        }

        if (filteredDocuments.isEmpty()) {
            return getNoResultsMessage();
        }

        return filteredDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private String getNoResultsMessage() {
        return "No documentation found for the query. Try rephrasing your query or using another tool.";
    }
}

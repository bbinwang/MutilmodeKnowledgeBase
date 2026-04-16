package com.multimode.kb.data.repository;

import android.util.Log;

import com.multimode.kb.data.local.HybridSearchDataSource;
import com.multimode.kb.domain.entity.SearchResult;
import com.multimode.kb.domain.service.EmbeddingService;
import com.multimode.kb.domain.service.HybridSearchService;
import com.multimode.kb.llm.QueryRewriter;
import com.multimode.kb.llm.RerankService;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Implementation of HybridSearchService.
 * Flow: query rewrite → embed → hybrid search (vector + keyword + RRF) → rerank.
 */
public class HybridSearchServiceImpl implements HybridSearchService {

    private static final String TAG = "HybridSearch";

    private final EmbeddingService embeddingService;
    private final HybridSearchDataSource hybridSearchDataSource;
    private QueryRewriter queryRewriter; // optional, set via setter
    private RerankService rerankService; // optional, set via setter

    public HybridSearchServiceImpl(EmbeddingService embeddingService,
                                   HybridSearchDataSource hybridSearchDataSource) {
        this.embeddingService = embeddingService;
        this.hybridSearchDataSource = hybridSearchDataSource;
    }

    public void setQueryRewriter(QueryRewriter queryRewriter) {
        this.queryRewriter = queryRewriter;
    }

    public void setRerankService(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public Single<List<SearchResult>> search(String queryText, int topN) {
        return search(queryText, topN, -1);
    }

    @Override
    public Single<List<SearchResult>> search(String queryText, int topN, long documentId) {
        return Single.fromCallable(() -> {
                    // Step 1: Query rewrite (optional, non-blocking on failure)
                    String searchQuery = queryText;
                    if (queryRewriter != null) {
                        String rewritten = queryRewriter.rewrite(queryText);
                        if (!rewritten.equals(queryText)) {
                            Log.i(TAG, "Query rewritten: \"" + queryText + "\" → \"" + rewritten + "\"");
                            searchQuery = rewritten;
                        }
                    }
                    return searchQuery;
                })
                .flatMap(searchQuery -> embeddingService.embed(searchQuery)
                        .map(queryVector -> {
                            // Step 2: Hybrid search (vector + keyword + RRF fusion)
                            List<SearchResult> results = hybridSearchDataSource.search(
                                    queryVector, searchQuery, topN, documentId);

                            // Step 3: Rerank (optional, improves precision)
                            if (rerankService != null && !results.isEmpty()) {
                                List<SearchResult> reranked = rerankService.rerank(queryText, results);
                                if (reranked != results) {
                                    Log.i(TAG, "Reranked " + results.size() + " results");
                                    results = reranked;
                                }
                            }
                            return results;
                        }));
    }
}

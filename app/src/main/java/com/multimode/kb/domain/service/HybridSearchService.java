package com.multimode.kb.domain.service;

import com.multimode.kb.domain.entity.SearchResult;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Service interface for hybrid search (vector + keyword).
 */
public interface HybridSearchService {
    Single<List<SearchResult>> search(String queryText, int topN);

    /**
     * Scoped search: filter to a specific document.
     *
     * @param documentId scope to this document (-1 = all documents)
     */
    Single<List<SearchResult>> search(String queryText, int topN, long documentId);
}

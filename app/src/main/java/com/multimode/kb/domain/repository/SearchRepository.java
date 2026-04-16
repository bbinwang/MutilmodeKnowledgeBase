package com.multimode.kb.domain.repository;

import com.multimode.kb.domain.entity.SearchResult;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

public interface SearchRepository {

    Single<List<SearchResult>> hybridSearch(String query, int topN);

    /**
     * Scoped search: only return results from the specified document.
     *
     * @param query      search query
     * @param topN       max results
     * @param documentId scope to this document (-1 = global)
     */
    Single<List<SearchResult>> hybridSearch(String query, int topN, long documentId);
}

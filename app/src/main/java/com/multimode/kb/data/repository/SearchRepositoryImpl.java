package com.multimode.kb.data.repository;

import com.multimode.kb.domain.entity.SearchResult;
import com.multimode.kb.domain.repository.SearchRepository;
import com.multimode.kb.domain.service.HybridSearchService;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

public class SearchRepositoryImpl implements SearchRepository {

    private final HybridSearchService hybridSearchService;

    public SearchRepositoryImpl(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @Override
    public Single<List<SearchResult>> hybridSearch(String query, int topN) {
        return hybridSearchService.search(query, topN, -1);
    }

    @Override
    public Single<List<SearchResult>> hybridSearch(String query, int topN, long documentId) {
        return hybridSearchService.search(query, topN, documentId);
    }
}

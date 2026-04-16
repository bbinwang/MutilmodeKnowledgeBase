package com.multimode.kb.domain.service;

import com.multimode.kb.domain.entity.SearchResult;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Service interface for RAG (Retrieval-Augmented Generation).
 */
public interface RagService {
    Single<String> ask(String question, List<SearchResult> context);
}

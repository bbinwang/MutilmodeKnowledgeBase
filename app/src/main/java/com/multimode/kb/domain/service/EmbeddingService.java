package com.multimode.kb.domain.service;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Service interface for generating text embeddings via cloud API.
 */
public interface EmbeddingService {
    Single<float[]> embed(String text);

    Single<List<float[]>> embedBatch(List<String> texts);
}

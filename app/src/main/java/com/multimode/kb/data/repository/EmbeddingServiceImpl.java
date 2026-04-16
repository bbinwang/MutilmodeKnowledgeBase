package com.multimode.kb.data.repository;

import com.multimode.kb.domain.service.EmbeddingService;
import com.multimode.kb.llm.EmbeddingClient;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Implementation of EmbeddingService using EmbeddingClient.
 */
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingClient embeddingClient;

    public EmbeddingServiceImpl(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public Single<float[]> embed(String text) {
        return Single.fromCallable(() -> embeddingClient.embed(text));
    }

    @Override
    public Single<List<float[]>> embedBatch(List<String> texts) {
        return Single.fromCallable(() -> embeddingClient.embedBatch(texts));
    }
}

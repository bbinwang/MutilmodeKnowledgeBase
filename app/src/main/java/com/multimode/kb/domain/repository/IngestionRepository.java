package com.multimode.kb.domain.repository;

import com.multimode.kb.domain.entity.DocumentSegment;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface IngestionRepository {

    Single<List<Long>> insertSegments(List<DocumentSegment> segments);

    Completable updateSegmentEmbedding(long segmentId, float[] embedding);

    Single<List<DocumentSegment>> getSegmentsWithoutEmbedding();

    Completable indexSegmentForFts(long segmentId, long documentId,
                                    String documentName, String sourceLocation, String content);

    Completable deleteDocumentSegments(long documentId);
}

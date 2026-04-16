package com.multimode.kb.data.repository;

import com.multimode.kb.data.local.fts.FtsDataSource;
import com.multimode.kb.data.local.objectbox.DocumentSegmentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.domain.entity.DocumentSegment;
import com.multimode.kb.domain.repository.IngestionRepository;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public class IngestionRepositoryImpl implements IngestionRepository {

    private final ObjectBoxDataSource objectBoxDataSource;
    private final FtsDataSource ftsDataSource;

    public IngestionRepositoryImpl(ObjectBoxDataSource objectBoxDataSource,
                                   FtsDataSource ftsDataSource) {
        this.objectBoxDataSource = objectBoxDataSource;
        this.ftsDataSource = ftsDataSource;
    }

    @Override
    public Single<List<Long>> insertSegments(List<DocumentSegment> segments) {
        return Single.fromCallable(() -> {
            List<Long> ids = new ArrayList<>();
            for (DocumentSegment seg : segments) {
                DocumentSegmentEntity entity = toEntity(seg);
                long id = objectBoxDataSource.insertSegment(entity);
                seg.setId(id);
                ids.add(id);
            }
            return ids;
        });
    }

    @Override
    public Completable updateSegmentEmbedding(long segmentId, float[] embedding) {
        return Completable.fromAction(() -> {
            DocumentSegmentEntity entity = objectBoxDataSource.getSegment(segmentId);
            if (entity != null) {
                entity.embedding = embedding;
                objectBoxDataSource.updateSegment(entity);
            }
        });
    }

    @Override
    public Single<List<DocumentSegment>> getSegmentsWithoutEmbedding() {
        return Single.fromCallable(() -> {
            List<DocumentSegmentEntity> entities = objectBoxDataSource.getSegmentsWithoutEmbedding();
            List<DocumentSegment> segments = new ArrayList<>();
            for (DocumentSegmentEntity e : entities) {
                segments.add(toDomain(e));
            }
            return segments;
        });
    }

    @Override
    public Completable indexSegmentForFts(long segmentId, long documentId,
                                           String documentName, String sourceLocation,
                                           String content) {
        return Completable.fromAction(() ->
                ftsDataSource.insert(segmentId, documentId, documentName, sourceLocation, content)
        );
    }

    @Override
    public Completable deleteDocumentSegments(long documentId) {
        return Completable.fromAction(() -> {
            objectBoxDataSource.getSegmentsByDocument(documentId); // verify exists
            ftsDataSource.deleteByDocumentId(documentId);
            objectBoxDataSource.deleteDocument(documentId);
        });
    }

    private DocumentSegmentEntity toEntity(DocumentSegment seg) {
        DocumentSegmentEntity entity = new DocumentSegmentEntity();
        entity.id = seg.getId();
        entity.documentId = seg.getDocumentId();
        entity.segmentIndex = seg.getSegmentIndex();
        entity.text = seg.getText();
        entity.metadataJson = seg.getMetadataJson();
        entity.embedding = seg.getEmbedding();
        return entity;
    }

    private DocumentSegment toDomain(DocumentSegmentEntity entity) {
        DocumentSegment seg = new DocumentSegment();
        seg.setId(entity.id);
        seg.setDocumentId(entity.documentId);
        seg.setSegmentIndex(entity.segmentIndex);
        seg.setText(entity.text);
        seg.setMetadataJson(entity.metadataJson);
        seg.setEmbedding(entity.embedding);
        return seg;
    }
}

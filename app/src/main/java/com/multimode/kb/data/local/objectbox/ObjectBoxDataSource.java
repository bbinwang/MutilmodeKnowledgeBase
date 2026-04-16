package com.multimode.kb.data.local.objectbox;

import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;

public class ObjectBoxDataSource {

    private final BoxStore boxStore;
    private final Box<KbDocumentEntity> documentBox;
    private final Box<DocumentSegmentEntity> segmentBox;

    public ObjectBoxDataSource(BoxStore boxStore) {
        this.boxStore = boxStore;
        this.documentBox = boxStore.boxFor(KbDocumentEntity.class);
        this.segmentBox = boxStore.boxFor(DocumentSegmentEntity.class);
    }

    // ---- Document CRUD ----

    public long insertDocument(KbDocumentEntity document) {
        return documentBox.put(document);
    }

    public KbDocumentEntity getDocument(long id) {
        return documentBox.get(id);
    }

    public List<KbDocumentEntity> getAllDocuments() {
        return documentBox.query().orderDesc(KbDocumentEntity_.createdAt).build().find();
    }

    public void updateDocument(KbDocumentEntity document) {
        document.updatedAt = System.currentTimeMillis();
        documentBox.put(document);
    }

    public void deleteDocument(long documentId) {
        segmentBox.query().equal(DocumentSegmentEntity_.documentId, documentId).build().remove();
        documentBox.remove(documentId);
    }

    // ---- Segment CRUD ----

    public long insertSegment(DocumentSegmentEntity segment) {
        return segmentBox.put(segment);
    }

    public List<Long> insertSegments(List<DocumentSegmentEntity> segments) {
        return segmentBox.put(segments);
    }

    public DocumentSegmentEntity getSegment(long id) {
        return segmentBox.get(id);
    }

    public List<DocumentSegmentEntity> getSegmentsByDocument(long documentId) {
        return segmentBox.query()
                .equal(DocumentSegmentEntity_.documentId, documentId)
                .orderAsc(DocumentSegmentEntity_.segmentIndex)
                .build().find();
    }

    public List<DocumentSegmentEntity> getSegmentsWithoutEmbedding() {
        return segmentBox.query()
                .isNull(DocumentSegmentEntity_.embedding)
                .build().find();
    }

    public void updateSegment(DocumentSegmentEntity segment) {
        segmentBox.put(segment);
    }

    public void updateSegments(List<DocumentSegmentEntity> segments) {
        segmentBox.put(segments);
    }

    // ---- Vector Search ----

    public List<DocumentSegmentEntity> searchByVector(float[] queryVector, int topK) {
        return segmentBox.query()
                .nearestNeighbors(DocumentSegmentEntity_.embedding, queryVector, topK)
                .build()
                .find();
    }

    public BoxStore getBoxStore() {
        return boxStore;
    }
}

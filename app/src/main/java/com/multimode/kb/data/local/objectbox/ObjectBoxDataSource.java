package com.multimode.kb.data.local.objectbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;

public class ObjectBoxDataSource {

    private final BoxStore boxStore;
    private final Box<KbDocumentEntity> documentBox;
    private final Box<DocumentSegmentEntity> segmentBox;
    private final Box<TrackedDirectoryEntity> directoryBox;

    public ObjectBoxDataSource(BoxStore boxStore) {
        this.boxStore = boxStore;
        this.documentBox = boxStore.boxFor(KbDocumentEntity.class);
        this.segmentBox = boxStore.boxFor(DocumentSegmentEntity.class);
        this.directoryBox = boxStore.boxFor(TrackedDirectoryEntity.class);
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

    public void insertDocuments(List<KbDocumentEntity> documents) {
        documentBox.put(documents);
    }

    public List<KbDocumentEntity> getDocumentsByDirectory(long directoryId) {
        return documentBox.query()
                .equal(KbDocumentEntity_.directoryId, directoryId)
                .order(KbDocumentEntity_.relativePath)
                .build().find();
    }

    public List<KbDocumentEntity> getDocumentsByStatus(String... statuses) {
        List<KbDocumentEntity> result = new ArrayList<>();
        for (String status : statuses) {
            result.addAll(documentBox.query()
                    .equal(KbDocumentEntity_.status, status, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().find());
        }
        return result;
    }

    public void deleteDocumentsByDirectory(long directoryId) {
        List<KbDocumentEntity> docs = getDocumentsByDirectory(directoryId);
        for (KbDocumentEntity doc : docs) {
            segmentBox.query().equal(DocumentSegmentEntity_.documentId, doc.id).build().remove();
        }
        documentBox.query().equal(KbDocumentEntity_.directoryId, directoryId).build().remove();
    }

    // ---- Directory CRUD ----

    public long insertDirectory(TrackedDirectoryEntity dir) {
        return directoryBox.put(dir);
    }

    public TrackedDirectoryEntity getDirectory(long id) {
        return directoryBox.get(id);
    }

    public List<TrackedDirectoryEntity> getAllDirectories() {
        return directoryBox.query().orderDesc(TrackedDirectoryEntity_.createdAt).build().find();
    }

    public void updateDirectory(TrackedDirectoryEntity dir) {
        dir.updatedAt = System.currentTimeMillis();
        directoryBox.put(dir);
    }

    public void deleteDirectory(long id) {
        deleteDocumentsByDirectory(id);
        directoryBox.remove(id);
    }

    public List<TrackedDirectoryEntity> getDirectoriesByStatus(String status) {
        return directoryBox.query()
                .equal(TrackedDirectoryEntity_.status, status, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().find();
    }

    // ---- Segment CRUD ----

    public long insertSegment(DocumentSegmentEntity segment) {
        return segmentBox.put(segment);
    }

    public void insertSegments(List<DocumentSegmentEntity> segments) {
        segmentBox.put(segments);
    }

    public DocumentSegmentEntity getSegment(long id) {
        return segmentBox.get(id);
    }

    public List<DocumentSegmentEntity> getSegmentsByDocument(long documentId) {
        return segmentBox.query()
                .equal(DocumentSegmentEntity_.documentId, documentId)
                .order(DocumentSegmentEntity_.segmentIndex)
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

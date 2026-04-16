package com.multimode.kb.data.repository;

import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.domain.entity.DocumentStatus;
import com.multimode.kb.domain.entity.KbDocument;
import com.multimode.kb.domain.repository.DocumentRepository;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public class DocumentRepositoryImpl implements DocumentRepository {

    private final ObjectBoxDataSource dataSource;

    public DocumentRepositoryImpl(ObjectBoxDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Single<Long> addDocument(KbDocument document) {
        return Single.fromCallable(() -> {
            KbDocumentEntity entity = toEntity(document);
            long id = dataSource.insertDocument(entity);
            document.setId(id);
            return id;
        });
    }

    @Override
    public Single<KbDocument> getDocument(long id) {
        return Single.fromCallable(() -> {
            KbDocumentEntity entity = dataSource.getDocument(id);
            return entity != null ? toDomain(entity) : null;
        });
    }

    @Override
    public Single<List<KbDocument>> getAllDocuments() {
        return Single.fromCallable(() -> {
            List<KbDocumentEntity> entities = dataSource.getAllDocuments();
            List<KbDocument> docs = new ArrayList<>();
            for (KbDocumentEntity e : entities) {
                docs.add(toDomain(e));
            }
            return docs;
        });
    }

    @Override
    public Completable updateDocument(KbDocument document) {
        return Completable.fromAction(() -> {
            KbDocumentEntity entity = toEntity(document);
            dataSource.updateDocument(entity);
        });
    }

    @Override
    public Completable deleteDocument(long documentId) {
        return Completable.fromAction(() -> dataSource.deleteDocument(documentId));
    }

    private KbDocumentEntity toEntity(KbDocument doc) {
        KbDocumentEntity entity = new KbDocumentEntity();
        entity.id = doc.getId();
        entity.fileName = doc.getFileName();
        entity.filePath = doc.getFilePath();
        entity.mimeType = doc.getMimeType();
        entity.fileSize = doc.getFileSize();
        entity.status = doc.getStatus().name();
        entity.errorMessage = doc.getErrorMessage();
        entity.totalSegments = doc.getTotalSegments();
        entity.createdAt = doc.getCreatedAt();
        entity.updatedAt = doc.getUpdatedAt();
        return entity;
    }

    private KbDocument toDomain(KbDocumentEntity entity) {
        KbDocument doc = new KbDocument();
        doc.setId(entity.id);
        doc.setFileName(entity.fileName);
        doc.setFilePath(entity.filePath);
        doc.setMimeType(entity.mimeType);
        doc.setFileSize(entity.fileSize);
        doc.setStatus(DocumentStatus.fromString(entity.status));
        doc.setErrorMessage(entity.errorMessage);
        doc.setTotalSegments(entity.totalSegments);
        doc.setCreatedAt(entity.createdAt);
        doc.setUpdatedAt(entity.updatedAt);
        return doc;
    }
}

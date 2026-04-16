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
    public Single<List<KbDocument>> getDocumentsByDirectory(long directoryId) {
        return Single.fromCallable(() -> {
            List<KbDocumentEntity> entities = dataSource.getDocumentsByDirectory(directoryId);
            List<KbDocument> docs = new ArrayList<>();
            for (KbDocumentEntity e : entities) {
                docs.add(toDomain(e));
            }
            return docs;
        });
    }

    @Override
    public Single<List<KbDocument>> getDocumentsByStatus(DocumentStatus... statuses) {
        return Single.fromCallable(() -> {
            String[] statusNames = new String[statuses.length];
            for (int i = 0; i < statuses.length; i++) {
                statusNames[i] = statuses[i].name();
            }
            List<KbDocumentEntity> entities = dataSource.getDocumentsByStatus(statusNames);
            List<KbDocument> docs = new ArrayList<>();
            for (KbDocumentEntity e : entities) {
                docs.add(toDomain(e));
            }
            return docs;
        });
    }

    @Override
    public Single<List<Long>> addDocumentsBatch(List<KbDocument> documents) {
        return Single.fromCallable(() -> {
            List<KbDocumentEntity> entities = new ArrayList<>();
            for (KbDocument doc : documents) {
                entities.add(toEntity(doc));
            }
            return dataSource.insertDocuments(entities);
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

    @Override
    public Completable deleteDocumentsByDirectory(long directoryId) {
        return Completable.fromAction(() -> dataSource.deleteDocumentsByDirectory(directoryId));
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
        entity.directoryId = doc.getDirectoryId();
        entity.fileLastModified = doc.getFileLastModified();
        entity.relativePath = doc.getRelativePath();
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
        doc.setDirectoryId(entity.directoryId);
        doc.setFileLastModified(entity.fileLastModified);
        doc.setRelativePath(entity.relativePath);
        return doc;
    }
}

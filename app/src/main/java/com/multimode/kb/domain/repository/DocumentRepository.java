package com.multimode.kb.domain.repository;

import com.multimode.kb.domain.entity.KbDocument;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface DocumentRepository {

    Single<Long> addDocument(KbDocument document);

    Single<KbDocument> getDocument(long id);

    Single<List<KbDocument>> getAllDocuments();

    Completable updateDocument(KbDocument document);

    Completable deleteDocument(long documentId);
}

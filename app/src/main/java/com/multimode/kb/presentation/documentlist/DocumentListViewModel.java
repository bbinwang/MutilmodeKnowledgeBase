package com.multimode.kb.presentation.documentlist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.multimode.kb.domain.entity.KbDocument;
import com.multimode.kb.domain.repository.DocumentRepository;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class DocumentListViewModel extends ViewModel {

    private final DocumentRepository documentRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<KbDocument>> documents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private long currentDirectoryId = 0;

    public DocumentListViewModel(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public LiveData<List<KbDocument>> getDocuments() { return documents; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadDocuments() {
        currentDirectoryId = 0;
        loading.setValue(true);
        disposables.add(
                documentRepository.getAllDocuments()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                docs -> {
                                    documents.setValue(docs);
                                    loading.setValue(false);
                                },
                                throwable -> {
                                    error.setValue(throwable.getMessage());
                                    loading.setValue(false);
                                }
                        )
        );
    }

    public void loadDocumentsByDirectory(long directoryId) {
        currentDirectoryId = directoryId;
        loading.setValue(true);
        disposables.add(
                documentRepository.getDocumentsByDirectory(directoryId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                docs -> {
                                    documents.setValue(docs);
                                    loading.setValue(false);
                                },
                                throwable -> {
                                    error.setValue(throwable.getMessage());
                                    loading.setValue(false);
                                }
                        )
        );
    }

    public void deleteDocument(long documentId) {
        disposables.add(
                documentRepository.deleteDocument(documentId)
                        .andThen(currentDirectoryId > 0
                                ? documentRepository.getDocumentsByDirectory(currentDirectoryId)
                                : documentRepository.getAllDocuments())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                docs -> documents.setValue(docs),
                                throwable -> error.setValue(throwable.getMessage())
                        )
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}

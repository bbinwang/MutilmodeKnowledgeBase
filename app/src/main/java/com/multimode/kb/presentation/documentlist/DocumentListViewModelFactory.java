package com.multimode.kb.presentation.documentlist;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.multimode.kb.domain.repository.DocumentRepository;

public class DocumentListViewModelFactory implements ViewModelProvider.Factory {

    private final DocumentRepository documentRepository;

    public DocumentListViewModelFactory(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(DocumentListViewModel.class)) {
            return modelClass.cast(new DocumentListViewModel(documentRepository));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
    }
}

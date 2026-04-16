package com.multimode.kb.presentation.directory;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.multimode.kb.domain.repository.DirectoryRepository;

public class DirectoryListViewModelFactory implements ViewModelProvider.Factory {

    private final DirectoryRepository directoryRepository;

    public DirectoryListViewModelFactory(DirectoryRepository directoryRepository) {
        this.directoryRepository = directoryRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(DirectoryListViewModel.class)) {
            return modelClass.cast(new DirectoryListViewModel(directoryRepository));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
    }
}

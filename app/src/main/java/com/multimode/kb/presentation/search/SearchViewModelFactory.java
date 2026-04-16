package com.multimode.kb.presentation.search;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.multimode.kb.domain.repository.SearchRepository;

public class SearchViewModelFactory implements ViewModelProvider.Factory {

    private final SearchRepository searchRepository;

    public SearchViewModelFactory(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(SearchViewModel.class)) {
            return modelClass.cast(new SearchViewModel(searchRepository));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
    }
}

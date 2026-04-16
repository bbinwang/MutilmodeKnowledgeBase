package com.multimode.kb.presentation.chat;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.multimode.kb.domain.repository.SearchRepository;
import com.multimode.kb.domain.service.RagService;

public class ChatViewModelFactory implements ViewModelProvider.Factory {

    private final SearchRepository searchRepository;
    private final RagService ragService;

    public ChatViewModelFactory(SearchRepository searchRepository, RagService ragService) {
        this.searchRepository = searchRepository;
        this.ragService = ragService;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            return modelClass.cast(new ChatViewModel(searchRepository, ragService));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
    }
}

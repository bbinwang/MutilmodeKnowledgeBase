package com.multimode.kb.presentation.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.multimode.kb.domain.entity.SearchResult;
import com.multimode.kb.domain.repository.SearchRepository;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SearchViewModel extends ViewModel {

    private final SearchRepository searchRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<SearchResult>> results = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public SearchViewModel(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public LiveData<List<SearchResult>> getResults() { return results; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) return;

        loading.setValue(true);
        disposables.add(
                searchRepository.hybridSearch(query.trim(), 20)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                searchResults -> {
                                    results.setValue(searchResults);
                                    loading.setValue(false);
                                },
                                throwable -> {
                                    error.setValue(throwable.getMessage());
                                    loading.setValue(false);
                                }
                        )
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}

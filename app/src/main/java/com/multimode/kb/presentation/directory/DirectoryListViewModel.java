package com.multimode.kb.presentation.directory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.multimode.kb.domain.entity.TrackedDirectory;
import com.multimode.kb.domain.repository.DirectoryRepository;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class DirectoryListViewModel extends ViewModel {

    private final DirectoryRepository directoryRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<TrackedDirectory>> directories = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public DirectoryListViewModel(DirectoryRepository directoryRepository) {
        this.directoryRepository = directoryRepository;
    }

    public LiveData<List<TrackedDirectory>> getDirectories() { return directories; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadDirectories() {
        loading.setValue(true);
        disposables.add(directoryRepository.getAllDirectories()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dirs -> {
                    directories.setValue(dirs);
                    loading.setValue(false);
                }, throwable -> {
                    error.setValue(throwable.getMessage());
                    loading.setValue(false);
                }));
    }

    public void deleteDirectory(long directoryId) {
        disposables.add(directoryRepository.deleteDirectory(directoryId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> loadDirectories(),
                        throwable -> error.setValue(throwable.getMessage())));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}

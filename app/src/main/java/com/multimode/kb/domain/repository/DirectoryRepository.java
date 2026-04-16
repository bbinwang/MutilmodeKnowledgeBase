package com.multimode.kb.domain.repository;

import com.multimode.kb.domain.entity.DirectoryStatus;
import com.multimode.kb.domain.entity.TrackedDirectory;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface DirectoryRepository {

    Single<Long> addDirectory(TrackedDirectory directory);

    Single<TrackedDirectory> getDirectory(long id);

    Single<List<TrackedDirectory>> getAllDirectories();

    Single<List<TrackedDirectory>> getDirectoriesByStatus(DirectoryStatus status);

    Completable updateDirectory(TrackedDirectory directory);

    Completable deleteDirectory(long directoryId);
}

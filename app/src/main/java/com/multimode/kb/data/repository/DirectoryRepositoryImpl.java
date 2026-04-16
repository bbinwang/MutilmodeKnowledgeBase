package com.multimode.kb.data.repository;

import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.data.local.objectbox.TrackedDirectoryEntity;
import com.multimode.kb.domain.entity.DirectoryStatus;
import com.multimode.kb.domain.entity.TrackedDirectory;
import com.multimode.kb.domain.repository.DirectoryRepository;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public class DirectoryRepositoryImpl implements DirectoryRepository {

    private final ObjectBoxDataSource dataSource;

    public DirectoryRepositoryImpl(ObjectBoxDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Single<Long> addDirectory(TrackedDirectory directory) {
        return Single.fromCallable(() -> {
            TrackedDirectoryEntity entity = toEntity(directory);
            long id = dataSource.insertDirectory(entity);
            directory.setId(id);
            return id;
        });
    }

    @Override
    public Single<TrackedDirectory> getDirectory(long id) {
        return Single.fromCallable(() -> {
            TrackedDirectoryEntity entity = dataSource.getDirectory(id);
            return entity != null ? toDomain(entity) : null;
        });
    }

    @Override
    public Single<List<TrackedDirectory>> getAllDirectories() {
        return Single.fromCallable(() -> {
            List<TrackedDirectoryEntity> entities = dataSource.getAllDirectories();
            List<TrackedDirectory> dirs = new ArrayList<>();
            for (TrackedDirectoryEntity e : entities) {
                dirs.add(toDomain(e));
            }
            return dirs;
        });
    }

    @Override
    public Single<List<TrackedDirectory>> getDirectoriesByStatus(DirectoryStatus status) {
        return Single.fromCallable(() -> {
            List<TrackedDirectoryEntity> entities = dataSource.getDirectoriesByStatus(status.name());
            List<TrackedDirectory> dirs = new ArrayList<>();
            for (TrackedDirectoryEntity e : entities) {
                dirs.add(toDomain(e));
            }
            return dirs;
        });
    }

    @Override
    public Completable updateDirectory(TrackedDirectory directory) {
        return Completable.fromAction(() -> {
            TrackedDirectoryEntity entity = toEntity(directory);
            dataSource.updateDirectory(entity);
        });
    }

    @Override
    public Completable deleteDirectory(long directoryId) {
        return Completable.fromAction(() -> dataSource.deleteDirectory(directoryId));
    }

    private TrackedDirectoryEntity toEntity(TrackedDirectory dir) {
        TrackedDirectoryEntity entity = new TrackedDirectoryEntity();
        entity.id = dir.getId();
        entity.treeUri = dir.getTreeUri();
        entity.displayName = dir.getDisplayName();
        entity.status = dir.getStatus().name();
        entity.totalFiles = dir.getTotalFiles();
        entity.indexedFiles = dir.getIndexedFiles();
        entity.lastScannedAt = dir.getLastScannedAt();
        entity.createdAt = dir.getCreatedAt();
        entity.updatedAt = dir.getUpdatedAt();
        return entity;
    }

    private TrackedDirectory toDomain(TrackedDirectoryEntity entity) {
        TrackedDirectory dir = new TrackedDirectory();
        dir.setId(entity.id);
        dir.setTreeUri(entity.treeUri);
        dir.setDisplayName(entity.displayName);
        dir.setStatus(DirectoryStatus.fromString(entity.status));
        dir.setTotalFiles(entity.totalFiles);
        dir.setIndexedFiles(entity.indexedFiles);
        dir.setLastScannedAt(entity.lastScannedAt);
        dir.setCreatedAt(entity.createdAt);
        dir.setUpdatedAt(entity.updatedAt);
        return dir;
    }
}

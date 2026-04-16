package com.multimode.kb.data.local.objectbox;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;

@Entity
public class TrackedDirectoryEntity {

    @Id
    public long id;

    @Index
    public String treeUri;
    public String displayName;
    public String status;           // IDLE, SCANNING, INGESTING, ERROR
    public int totalFiles;
    public int indexedFiles;
    public long lastScannedAt;
    public long createdAt;
    public long updatedAt;

    public TrackedDirectoryEntity() {
        this.status = "IDLE";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
}

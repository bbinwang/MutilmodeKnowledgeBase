package com.multimode.kb.domain.entity;

/**
 * Domain entity representing a tracked directory.
 */
public class TrackedDirectory {

    private long id;
    private String treeUri;
    private String displayName;
    private DirectoryStatus status;
    private int totalFiles;
    private int indexedFiles;
    private long lastScannedAt;
    private long createdAt;
    private long updatedAt;

    public TrackedDirectory() {
        this.status = DirectoryStatus.IDLE;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTreeUri() { return treeUri; }
    public void setTreeUri(String treeUri) { this.treeUri = treeUri; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public DirectoryStatus getStatus() { return status; }
    public void setStatus(DirectoryStatus status) { this.status = status; }

    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

    public int getIndexedFiles() { return indexedFiles; }
    public void setIndexedFiles(int indexedFiles) { this.indexedFiles = indexedFiles; }

    public long getLastScannedAt() { return lastScannedAt; }
    public void setLastScannedAt(long lastScannedAt) { this.lastScannedAt = lastScannedAt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

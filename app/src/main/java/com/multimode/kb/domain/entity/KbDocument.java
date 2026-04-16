package com.multimode.kb.domain.entity;

/**
 * Domain entity representing a knowledge base document.
 */
public class KbDocument {

    private long id;
    private String fileName;
    private String filePath;
    private String mimeType;
    private long fileSize;
    private DocumentStatus status;
    private String errorMessage;
    private int totalSegments;
    private long createdAt;
    private long updatedAt;

    // Directory tracking fields
    private long directoryId;
    private long fileLastModified;
    private String relativePath;

    public KbDocument() {
        this.status = DocumentStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getTotalSegments() { return totalSegments; }
    public void setTotalSegments(int totalSegments) { this.totalSegments = totalSegments; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getDirectoryId() { return directoryId; }
    public void setDirectoryId(long directoryId) { this.directoryId = directoryId; }

    public long getFileLastModified() { return fileLastModified; }
    public void setFileLastModified(long fileLastModified) { this.fileLastModified = fileLastModified; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
}

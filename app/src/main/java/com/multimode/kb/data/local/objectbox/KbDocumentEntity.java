package com.multimode.kb.data.local.objectbox;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;

@Entity
public class KbDocumentEntity {

    @Id
    public long id;

    @Index
    public String fileName;

    public String filePath;          // SAF URI string
    public String mimeType;          // application/pdf, image/png, audio/wav, video/mp4
    public long fileSize;
    public String status;            // PENDING, INGESTING, INGESTED, ERROR
    public String errorMessage;
    public int totalSegments;
    public long createdAt;
    public long updatedAt;

    public KbDocumentEntity() {
        this.status = "PENDING";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public KbDocumentEntity(String fileName, String filePath, String mimeType, long fileSize) {
        this();
        this.fileName = fileName;
        this.filePath = filePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }
}

package com.multimode.kb.data.local.objectbox;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.HnswIndex;

@Entity
public class DocumentSegmentEntity {

    @Id
    public long id;

    @Index
    public long documentId;

    public int segmentIndex;
    public String text;
    public String metadataJson;       // {"page":3} / {"timestamp":"00:01:23"} / {"frame":5}
    public long createdAt;

    @HnswIndex(dimensions = 1536)
    public float[] embedding;

    public DocumentSegmentEntity() {
        this.createdAt = System.currentTimeMillis();
    }

    public DocumentSegmentEntity(long documentId, int segmentIndex, String text, String metadataJson) {
        this();
        this.documentId = documentId;
        this.segmentIndex = segmentIndex;
        this.text = text;
        this.metadataJson = metadataJson;
    }
}

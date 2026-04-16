package com.multimode.kb.domain.entity;

/**
 * Domain entity representing a text segment from a document.
 */
public class DocumentSegment {

    private long id;
    private long documentId;
    private int segmentIndex;
    private String text;
    private String metadataJson;
    private float[] embedding;

    public DocumentSegment() {}

    public DocumentSegment(long documentId, int segmentIndex, String text, String metadataJson) {
        this.documentId = documentId;
        this.segmentIndex = segmentIndex;
        this.text = text;
        this.metadataJson = metadataJson;
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDocumentId() { return documentId; }
    public void setDocumentId(long documentId) { this.documentId = documentId; }

    public int getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(int segmentIndex) { this.segmentIndex = segmentIndex; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
}

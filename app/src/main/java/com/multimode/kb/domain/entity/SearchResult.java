package com.multimode.kb.domain.entity;

/**
 * A single search result from hybrid search, with RRF fusion score.
 */
public class SearchResult {

    private final long segmentId;
    private final long documentId;
    private final String text;
    private final String metadataJson;
    private final String documentName;
    private final String sourceLocation;
    private final double rrfScore;
    private final int vectorRank;
    private final int keywordRank;
    private final double bm25Score;

    // Media info for rich display
    private final String documentMimeType;   // e.g. "image/png", "video/mp4", "application/pdf"
    private final String documentFilePath;   // SAF URI string for source jump

    public SearchResult(long segmentId, long documentId, String text, String metadataJson,
                        String documentName, String sourceLocation, double rrfScore,
                        int vectorRank, int keywordRank, double bm25Score) {
        this(segmentId, documentId, text, metadataJson, documentName, sourceLocation,
                rrfScore, vectorRank, keywordRank, bm25Score, null, null);
    }

    public SearchResult(long segmentId, long documentId, String text, String metadataJson,
                        String documentName, String sourceLocation, double rrfScore,
                        int vectorRank, int keywordRank, double bm25Score,
                        String documentMimeType, String documentFilePath) {
        this.segmentId = segmentId;
        this.documentId = documentId;
        this.text = text;
        this.metadataJson = metadataJson;
        this.documentName = documentName;
        this.sourceLocation = sourceLocation;
        this.rrfScore = rrfScore;
        this.vectorRank = vectorRank;
        this.keywordRank = keywordRank;
        this.bm25Score = bm25Score;
        this.documentMimeType = documentMimeType;
        this.documentFilePath = documentFilePath;
    }

    public long getSegmentId() { return segmentId; }
    public long getDocumentId() { return documentId; }
    public String getText() { return text; }
    public String getMetadataJson() { return metadataJson; }
    public String getDocumentName() { return documentName; }
    public String getSourceLocation() { return sourceLocation; }
    public double getRrfScore() { return rrfScore; }
    public int getVectorRank() { return vectorRank; }
    public int getKeywordRank() { return keywordRank; }
    public double getBm25Score() { return bm25Score; }

    public boolean hasVectorMatch() { return vectorRank > 0; }
    public boolean hasKeywordMatch() { return keywordRank > 0; }

    public String getDocumentMimeType() { return documentMimeType; }
    public String getDocumentFilePath() { return documentFilePath; }

    /** Check if this result originates from a media file (image/audio/video). */
    public boolean isMedia() {
        return documentMimeType != null
                && (documentMimeType.startsWith("image/")
                || documentMimeType.startsWith("audio/")
                || documentMimeType.startsWith("video/"));
    }

    /** Check if this result originates from an image file. */
    public boolean isImage() {
        return documentMimeType != null && documentMimeType.startsWith("image/");
    }

    /** Check if this result originates from a video file. */
    public boolean isVideo() {
        return documentMimeType != null && documentMimeType.startsWith("video/");
    }
}

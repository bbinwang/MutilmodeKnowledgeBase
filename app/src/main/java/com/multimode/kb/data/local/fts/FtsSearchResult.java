package com.multimode.kb.data.local.fts;

/**
 * A single result from FTS5 full-text search with BM25 ranking.
 */
public class FtsSearchResult {

    public final long segmentId;
    public final long documentId;
    public final String documentName;
    public final String sourceLocation;
    public final String content;
    public final double bm25Score;

    public FtsSearchResult(long segmentId, long documentId, String documentName,
                           String sourceLocation, String content, double bm25Score) {
        this.segmentId = segmentId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.sourceLocation = sourceLocation;
        this.content = content;
        this.bm25Score = bm25Score;
    }
}

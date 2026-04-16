package com.multimode.kb.data.local;

import com.multimode.kb.data.local.fts.FtsDataSource;
import com.multimode.kb.data.local.fts.FtsSearchResult;
import com.multimode.kb.data.local.objectbox.DocumentSegmentEntity;
import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.domain.entity.SearchResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid search combining ObjectBox vector search (HNSW) and FTS5 keyword search,
 * merged using Reciprocal Rank Fusion (RRF).
 */
public class HybridSearchDataSource {

    private static final int RRF_K = 60; // RRF constant
    private static final int VECTOR_TOP_K = 20;
    private static final int KEYWORD_TOP_K = 20;

    private final ObjectBoxDataSource objectBoxDataSource;
    private final FtsDataSource ftsDataSource;

    public HybridSearchDataSource(ObjectBoxDataSource objectBoxDataSource,
                                  FtsDataSource ftsDataSource) {
        this.objectBoxDataSource = objectBoxDataSource;
        this.ftsDataSource = ftsDataSource;
    }

    /**
     * Perform hybrid search: vector similarity + keyword matching, fused with RRF.
     *
     * @param queryVector  embedding of the user's query
     * @param queryText    raw query text for FTS5
     * @param topN         number of final results to return
     * @return fused and ranked search results
     */
    public List<SearchResult> search(float[] queryVector, String queryText, int topN) {
        return search(queryVector, queryText, topN, -1);
    }

    /**
     * Scoped hybrid search: filter results to a specific document.
     *
     * @param documentId scope to this document (-1 = all documents)
     */
    public List<SearchResult> search(float[] queryVector, String queryText, int topN, long documentId) {
        // Run both searches
        List<DocumentSegmentEntity> vectorResults =
                objectBoxDataSource.searchByVector(queryVector, VECTOR_TOP_K);
        List<FtsSearchResult> keywordResults =
                ftsDataSource.search(queryText, KEYWORD_TOP_K);

        // Filter by documentId if scoped
        if (documentId > 0) {
            vectorResults.removeIf(seg -> seg.documentId != documentId);
            keywordResults.removeIf(fts -> fts.documentId != documentId);
        }

        // RRF fusion
        return rrfFusion(vectorResults, keywordResults, topN);
    }

    /**
     * Reciprocal Rank Fusion: rrf_score = sum(1 / (k + rank)) for each list.
     */
    private List<SearchResult> rrfFusion(List<DocumentSegmentEntity> vectorResults,
                                         List<FtsSearchResult> keywordResults,
                                         int topN) {
        Map<Long, SearchResultBuilder> scoreMap = new HashMap<>();

        // Vector search ranking (rank starts at 1)
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentSegmentEntity seg = vectorResults.get(i);
            int rank = i + 1;
            double rrfContribution = 1.0 / (RRF_K + rank);

            SearchResultBuilder builder = scoreMap.computeIfAbsent(
                    seg.id, id -> new SearchResultBuilder(seg));
            builder.vectorRank = rank;
            builder.rrfScore += rrfContribution;
        }

        // Keyword search ranking
        for (int i = 0; i < keywordResults.size(); i++) {
            FtsSearchResult fts = keywordResults.get(i);
            int rank = i + 1;
            double rrfContribution = 1.0 / (RRF_K + rank);

            SearchResultBuilder builder = scoreMap.computeIfAbsent(
                    fts.segmentId, id -> {
                        // Fetch from ObjectBox if not already present
                        DocumentSegmentEntity seg = objectBoxDataSource.getSegment(id);
                        return new SearchResultBuilder(seg, fts);
                    });
            builder.keywordRank = rank;
            builder.bm25Score = fts.bm25Score;
            builder.rrfScore += rrfContribution;
        }

        // Enrich results with document metadata (mimeType, filePath)
        Map<Long, KbDocumentEntity> docCache = new HashMap<>();
        for (SearchResultBuilder builder : scoreMap.values()) {
            KbDocumentEntity doc = docCache.computeIfAbsent(builder.documentId,
                    id -> objectBoxDataSource.getDocument(id));
            builder.enrichFromDocument(doc);
        }

        // Sort by RRF score descending
        List<SearchResult> results = new ArrayList<>();
        scoreMap.values().stream()
                .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
                .limit(topN)
                .forEach(builder -> results.add(builder.build()));

        return results;
    }

    private static class SearchResultBuilder {
        long segmentId;
        long documentId;
        String text;
        String metadataJson;
        double rrfScore;
        int vectorRank;
        int keywordRank;
        double bm25Score;
        String documentName;
        String sourceLocation;
        String documentMimeType;
        String documentFilePath;

        SearchResultBuilder(DocumentSegmentEntity seg) {
            this.segmentId = seg.id;
            this.documentId = seg.documentId;
            this.text = seg.text;
            this.metadataJson = seg.metadataJson;
        }

        SearchResultBuilder(DocumentSegmentEntity seg, FtsSearchResult fts) {
            this(seg);
            this.documentName = fts.documentName;
            this.sourceLocation = fts.sourceLocation;
        }

        void enrichFromDocument(KbDocumentEntity doc) {
            if (doc != null) {
                if (documentName == null) documentName = doc.fileName;
                this.documentMimeType = doc.mimeType;
                this.documentFilePath = doc.filePath;
            }
        }

        SearchResult build() {
            return new SearchResult(segmentId, documentId, text, metadataJson,
                    documentName, sourceLocation, rrfScore, vectorRank, keywordRank, bm25Score,
                    documentMimeType, documentFilePath);
        }
    }
}

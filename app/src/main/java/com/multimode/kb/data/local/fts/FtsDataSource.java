package com.multimode.kb.data.local.fts;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class FtsDataSource {

    private static final String SEARCH_QUERY =
            "SELECT m.segment_id, m.document_id, m.document_name, m.source_location, " +
            "m.content, f.rank " +
            "FROM segments_fts f " +
            "JOIN fts_map m ON f.rowid = m.rowid " +
            "WHERE segments_fts MATCH ? " +
            "ORDER BY f.rank " +
            "LIMIT ?";

    private final FtsDatabase ftsDatabase;

    public FtsDataSource(FtsDatabase ftsDatabase) {
        this.ftsDatabase = ftsDatabase;
    }

    public long insert(long segmentId, long documentId, String documentName,
                       String sourceLocation, String content) {
        SQLiteDatabase db = ftsDatabase.getWritableDb();
        ContentValues values = new ContentValues();
        values.put("segment_id", segmentId);
        values.put("document_id", documentId);
        values.put("document_name", documentName);
        values.put("source_location", sourceLocation);
        values.put("content", content);
        return db.insert("fts_map", null, values);
    }

    public void insertBatch(List<FtsEntry> entries) {
        SQLiteDatabase db = ftsDatabase.getWritableDb();
        db.beginTransaction();
        try {
            for (FtsEntry entry : entries) {
                ContentValues values = new ContentValues();
                values.put("segment_id", entry.segmentId);
                values.put("document_id", entry.documentId);
                values.put("document_name", entry.documentName);
                values.put("source_location", entry.sourceLocation);
                values.put("content", entry.content);
                db.insert("fts_map", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * FTS5 keyword search returning BM25-ranked results.
     */
    public List<FtsSearchResult> search(String query, int limit) {
        SQLiteDatabase db = ftsDatabase.getReadableDb();
        List<FtsSearchResult> results = new ArrayList<>();
        String escapedQuery = escapeFtsQuery(query);

        try (Cursor cursor = db.rawQuery(SEARCH_QUERY, new String[]{escapedQuery, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                long segmentId = cursor.getLong(0);
                long documentId = cursor.getLong(1);
                String docName = cursor.getString(2);
                String sourceLoc = cursor.getString(3);
                String content = cursor.getString(4);
                double rank = cursor.getDouble(5);

                results.add(new FtsSearchResult(segmentId, documentId, docName, sourceLoc, content, rank));
            }
        }
        return results;
    }

    public void deleteByDocumentId(long documentId) {
        SQLiteDatabase db = ftsDatabase.getWritableDb();
        db.delete("fts_map", "document_id = ?", new String[]{String.valueOf(documentId)});
    }

    public void deleteBySegmentId(long segmentId) {
        SQLiteDatabase db = ftsDatabase.getWritableDb();
        db.delete("fts_map", "segment_id = ?", new String[]{String.valueOf(segmentId)});
    }

    private String escapeFtsQuery(String query) {
        String escaped = query.replaceAll("[\"'*:^#]", "");
        String[] terms = escaped.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.length; i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(terms[i]);
        }
        return sb.toString();
    }

    public static class FtsEntry {
        public final long segmentId;
        public final long documentId;
        public final String documentName;
        public final String sourceLocation;
        public final String content;

        public FtsEntry(long segmentId, long documentId, String documentName,
                        String sourceLocation, String content) {
            this.segmentId = segmentId;
            this.documentId = documentId;
            this.documentName = documentName;
            this.sourceLocation = sourceLocation;
            this.content = content;
        }
    }
}

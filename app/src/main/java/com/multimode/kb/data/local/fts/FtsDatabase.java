package com.multimode.kb.data.local.fts;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory;

public class FtsDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "kb_fts.db";
    private static final int DATABASE_VERSION = 1;

    private static final String CREATE_FTS_TABLE = """
        CREATE VIRTUAL TABLE IF NOT EXISTS segments_fts USING fts5(
            content,
            tokenize='unicode61'
        )
    """;

    private static final String CREATE_MAP_TABLE = """
        CREATE TABLE IF NOT EXISTS fts_map (
            rowid INTEGER PRIMARY KEY AUTOINCREMENT,
            segment_id INTEGER NOT NULL,
            document_id INTEGER NOT NULL,
            document_name TEXT NOT NULL,
            source_location TEXT,
            content TEXT NOT NULL
        )
    """;

    private static final String CREATE_INDEXES = """
        CREATE INDEX IF NOT EXISTS idx_fts_map_segment ON fts_map(segment_id);
        CREATE INDEX IF NOT EXISTS idx_fts_map_document ON fts_map(document_id)
    """;

    private static final String CREATE_TRIGGER_INSERT = """
        CREATE TRIGGER IF NOT EXISTS trg_fts_insert AFTER INSERT ON fts_map BEGIN
            INSERT INTO segments_fts(rowid, content) VALUES (NEW.rowid, NEW.content);
        END
    """;

    private static final String CREATE_TRIGGER_DELETE = """
        CREATE TRIGGER IF NOT EXISTS trg_fts_delete AFTER DELETE ON fts_map BEGIN
            INSERT INTO segments_fts(segments_fts, rowid, content) VALUES('delete', OLD.rowid, OLD.content);
        END
    """;

    public FtsDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION,
              new RequerySQLiteOpenHelperFactory());
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_MAP_TABLE);
        db.execSQL(CREATE_INDEXES);
        db.execSQL(CREATE_FTS_TABLE);
        db.execSQL(CREATE_TRIGGER_INSERT);
        db.execSQL(CREATE_TRIGGER_DELETE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS fts_map");
        db.execSQL("DROP TABLE IF EXISTS segments_fts");
        onCreate(db);
    }

    public SQLiteDatabase getReadableDb() {
        return getReadableDatabase();
    }

    public SQLiteDatabase getWritableDb() {
        return getWritableDatabase();
    }
}

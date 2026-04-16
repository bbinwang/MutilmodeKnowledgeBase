package com.multimode.kb.domain.service;

import android.net.Uri;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Service for parsing files into text segments.
 * Each segment has text and optional metadata (page number, etc.).
 */
public interface FileParserService {

    /**
     * Parse a file and return text segments with metadata.
     *
     * @param uri      SAF URI of the file
     * @param mimeType MIME type of the file
     * @return list of segments, each is [text, metadataJson]
     */
    Single<List<SegmentData>> parse(Uri uri, String mimeType);

    /**
     * A parsed text segment with metadata.
     */
    class SegmentData {
        public final String text;
        public final String metadataJson; // {"page":3} or null

        public SegmentData(String text, String metadataJson) {
            this.text = text;
            this.metadataJson = metadataJson;
        }
    }
}

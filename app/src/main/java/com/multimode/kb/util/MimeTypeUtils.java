package com.multimode.kb.util;

import java.util.Set;

/**
 * Centralized supported MIME type constants.
 */
public final class MimeTypeUtils {

    private static final Set<String> SUPPORTED_EXACT = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
    );

    private static final Set<String> SUPPORTED_PREFIXES = Set.of(
            "image/",
            "audio/",
            "video/"
    );

    private MimeTypeUtils() {}

    public static boolean isSupported(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return false;
        if (SUPPORTED_EXACT.contains(mimeType)) return true;
        for (String prefix : SUPPORTED_PREFIXES) {
            if (mimeType.startsWith(prefix)) return true;
        }
        return false;
    }
}

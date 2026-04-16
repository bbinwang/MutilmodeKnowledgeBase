package com.multimode.kb.data.scanner;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.documentfile.provider.DocumentFile;

/**
 * Scans SAF directory trees for supported files and detects changes (delta).
 */
public class DirectoryScanner {

    private static final String TAG = "DirScanner";

    public static class FileInfo {
        public String fileName;
        public String uriString;
        public String mimeType;
        public long fileSize;
        public long lastModified;
        public String relativePath;
    }

    public static class ScanResult {
        public List<FileInfo> newFiles = new ArrayList<>();
        public List<ModifiedFile> modifiedFiles = new ArrayList<>();
        public List<Long> deletedDocumentIds = new ArrayList<>();
        public int unchangedCount = 0;
    }

    public static class ModifiedFile {
        public FileInfo fileInfo;
        public long existingDocumentId;

        public ModifiedFile(FileInfo fileInfo, long existingDocumentId) {
            this.fileInfo = fileInfo;
            this.existingDocumentId = existingDocumentId;
        }
    }

    private final Context context;
    private final ObjectBoxDataSource dataSource;

    public DirectoryScanner(Context context, ObjectBoxDataSource dataSource) {
        this.context = context;
        this.dataSource = dataSource;
    }

    /**
     * Full scan: enumerate all supported files in directory tree.
     */
    public ScanResult scanFull(Uri treeUri, long directoryId) {
        ScanResult result = new ScanResult();

        List<FileInfo> liveFiles = new ArrayList<>();
        walkTree(DocumentFile.fromTreeUri(context, treeUri), "", liveFiles);
        Log.i(TAG, "Full scan found " + liveFiles.size() + " supported files");

        // Load existing docs for this directory (may have stale entries)
        Map<String, KbDocumentEntity> dbMap = loadDbMap(directoryId);

        for (FileInfo file : liveFiles) {
            KbDocumentEntity existing = dbMap.remove(file.relativePath);
            if (existing == null) {
                result.newFiles.add(file);
            } else if (file.lastModified != existing.fileLastModified
                    || file.fileSize != existing.fileSize) {
                result.modifiedFiles.add(new ModifiedFile(file, existing.id));
            } else {
                result.unchangedCount++;
            }
        }

        // Remaining in dbMap are deleted files
        for (KbDocumentEntity deleted : dbMap.values()) {
            result.deletedDocumentIds.add(deleted.id);
        }

        Log.i(TAG, "Scan result: " + result.newFiles.size() + " new, "
                + result.modifiedFiles.size() + " modified, "
                + result.deletedDocumentIds.size() + " deleted, "
                + result.unchangedCount + " unchanged");

        return result;
    }

    /**
     * Delta scan: same as full scan but used for re-scanning existing directories.
     */
    public ScanResult scanDelta(Uri treeUri, long directoryId) {
        return scanFull(treeUri, directoryId);
    }

    private void walkTree(DocumentFile dir, String prefix, List<FileInfo> output) {
        DocumentFile[] children = dir.listFiles();
        if (children == null) return;

        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                walkTree(child, prefix + (name != null ? name : "") + "/", output);
            } else if (child.isFile()) {
                String mime = child.getType();
                if (mime != null && MimeTypeUtils.isSupported(mime)) {
                    FileInfo info = new FileInfo();
                    info.fileName = child.getName();
                    info.uriString = child.getUri().toString();
                    info.mimeType = mime;
                    info.fileSize = child.length();
                    info.lastModified = child.lastModified();
                    info.relativePath = prefix + (child.getName() != null ? child.getName() : "");
                    output.add(info);
                }
            }
        }
    }

    private Map<String, KbDocumentEntity> loadDbMap(long directoryId) {
        List<KbDocumentEntity> docs = dataSource.getDocumentsByDirectory(directoryId);
        Map<String, KbDocumentEntity> map = new HashMap<>();
        for (KbDocumentEntity doc : docs) {
            if (doc.relativePath != null) {
                map.put(doc.relativePath, doc);
            }
        }
        return map;
    }
}

package com.multimode.kb.data.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.multimode.kb.domain.service.FileParserService;
import com.multimode.kb.llm.OmniClient;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;

/**
 * Implementation of FileParserService for PDF, DOCX, and PPTX files.
 * Optimized for large files (150MB PDF/PPTX).
 */
public class FileParserServiceImpl implements FileParserService {

    private static final String TAG = "FileParser";
    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;
    private static final int PDF_PAGES_PER_BATCH = 50; // Process in batches to limit memory
    private static final int OCR_MAX_PAGES = 50;     // Max pages to OCR (avoid excessive API calls)
    private static final float OCR_RENDER_DPI = 150f; // DPI for rendering scanned pages

    private final Context context;
    private OmniClient omniClient; // optional, for OCR

    private int ocrPagesProcessed = 0; // track OCR pages across batches
    private final Set<Integer> ocrPageSet = new HashSet<>(); // 1-based page numbers that were OCR'd

    public FileParserServiceImpl(Context context) {
        this.context = context;
    }

    /**
     * Set OmniClient for scanned PDF OCR. Optional — if not set, scanned pages are skipped.
     */
    public void setOmniClient(OmniClient omniClient) {
        this.omniClient = omniClient;
    }

    @Override
    public Single<List<SegmentData>> parse(Uri uri, String mimeType) {
        return Single.fromCallable(() -> {
            if (mimeType.contains("pdf")) {
                return parsePdf(uri);
            } else if (mimeType.contains("presentation") || mimeType.contains("pptx")
                    || mimeType.contains("ppt")) {
                return parsePptx(uri);
            } else if (mimeType.contains("officedocument") || mimeType.contains("docx")
                    || mimeType.contains("msword")) {
                return parseDocx(uri);
            } else {
                throw new UnsupportedOperationException("Unsupported MIME type: " + mimeType);
            }
        });
    }

    /**
     * Parse PDF with memory optimization:
     * - Uses temp file for scratch data (avoids holding all in memory)
     * - Processes pages in batches of 50
     * - Explicit GC between batches
     */
    private List<SegmentData> parsePdf(Uri uri) throws Exception {
        List<SegmentData> segments = new ArrayList<>();
        ocrPagesProcessed = 0;
        ocrPageSet.clear();

        // Use temp-file-only memory setting to avoid OOM on large PDFs
        MemoryUsageSetting memSetting = MemoryUsageSetting.setupTempFileOnly();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(is, memSetting)) {

            int totalPages = document.getNumberOfPages();
            Log.i(TAG, "PDF loaded: " + totalPages + " pages (temp-file memory mode)");

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = null;

            // Only create renderer if we have OmniClient for OCR
            if (omniClient != null) {
                renderer = new PDFRenderer(document);
            }

            // Process in batches to keep memory bounded
            for (int batchStart = 1; batchStart <= totalPages; batchStart += PDF_PAGES_PER_BATCH) {
                int batchEnd = Math.min(batchStart + PDF_PAGES_PER_BATCH - 1, totalPages);

                for (int page = batchStart; page <= batchEnd; page++) {
                    try {
                        stripper.setStartPage(page);
                        stripper.setEndPage(page);
                        String pageText = stripper.getText(document).trim();

                        if (pageText.isEmpty()) {
                            // Scanned page — try OCR if OmniClient available
                            if (renderer != null && ocrPagesProcessed < OCR_MAX_PAGES) {
                                pageText = ocrPage(renderer, page - 1); // 0-based index
                                if (!pageText.isEmpty()) {
                                    ocrPagesProcessed++;
                                    ocrPageSet.add(page);
                                    Log.i(TAG, "OCR page " + page + " success ("
                                            + ocrPagesProcessed + "/" + OCR_MAX_PAGES + ")");
                                }
                            }
                            if (pageText.isEmpty()) continue;
                        }

                        List<String> chunks = splitText(pageText);
                        for (String chunk : chunks) {
                            segments.add(new SegmentData(chunk,
                                    "{\"page\":" + page + ",\"total_pages\":" + totalPages
                                            + (ocrPageSet.contains(page) ? ",\"ocr\":true" : "") + "}"));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse page " + page + ": " + e.getMessage());
                    }
                }

                Log.i(TAG, "PDF batch done: pages " + batchStart + "-" + batchEnd
                        + ", segments so far: " + segments.size()
                        + ", OCR pages: " + ocrPagesProcessed);

                // Hint to GC between batches
                if (batchEnd < totalPages) {
                    System.gc();
                }
            }

            if (renderer != null) {
                renderer.dispose();
            }

            Log.i(TAG, "PDF parsed: " + totalPages + " pages, " + segments.size()
                    + " segments, " + ocrPagesProcessed + " pages OCR'd");
        }
        return segments;
    }

    /**
     * Render a PDF page to bitmap, convert to base64 JPEG, send to Omni for OCR.
     * @param pageIndex 0-based page index
     */
    private String ocrPage(PDFRenderer renderer, int pageIndex) {
        try {
            Bitmap bitmap = renderer.renderImageWithDPI(pageIndex, OCR_RENDER_DPI);
            if (bitmap == null) return "";

            // Compress to JPEG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            bitmap.recycle();

            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            Log.d(TAG, "OCR: rendered page " + (pageIndex + 1)
                    + ", image size=" + (base64.length() / 1024) + "KB");

            // Send to Omni for OCR description
            String ocrPrompt = "请识别并提取这张图片中的所有文字内容，保持原始格式和结构。"
                    + "如果有表格，用文本形式表示。只输出识别到的文字，不要解释。";
            String result = omniClient.describeImage(base64, ocrPrompt);

            return result != null ? result.trim() : "";
        } catch (Exception e) {
            Log.w(TAG, "OCR failed for page " + (pageIndex + 1) + ": " + e.getMessage());
            return "";
        }
    }


    /**
     * Parse PPTX (PowerPoint) — extract text from all slides.
     * For very large PPTX, uses SAX-based streaming in future.
     * Current implementation handles reasonable sizes well.
     */
    private List<SegmentData> parsePptx(Uri uri) throws Exception {
        List<SegmentData> segments = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             XMLSlideShow slideshow = new XMLSlideShow(is)) {

            List<XSLFSlide> slides = slideshow.getSlides();
            int totalSlides = slides.size();
            Log.i(TAG, "PPTX loaded: " + totalSlides + " slides");

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                StringBuilder slideText = new StringBuilder();

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String text = textShape.getText().trim();
                        if (!text.isEmpty()) {
                            slideText.append(text).append("\n");
                        }
                    }
                }

                String text = slideText.toString().trim();
                if (text.isEmpty()) continue;

                // Each slide is a segment (slides are usually concise)
                int slideNum = i + 1;
                if (text.length() <= CHUNK_SIZE * 2) {
                    // Short slide: one segment
                    segments.add(new SegmentData(text,
                            "{\"slide\":" + slideNum + ",\"total_slides\":" + totalSlides + "}"));
                } else {
                    // Long slide: split into chunks
                    List<String> chunks = splitText(text);
                    for (int j = 0; j < chunks.size(); j++) {
                        segments.add(new SegmentData(chunks.get(j),
                                "{\"slide\":" + slideNum + ",\"total_slides\":" + totalSlides
                                        + ",\"chunk\":" + (j + 1) + "}"));
                    }
                }
            }

            Log.i(TAG, "PPTX parsed: " + totalSlides + " slides, " + segments.size() + " segments");
        }
        return segments;
    }

    private List<SegmentData> parseDocx(Uri uri) throws Exception {
        List<SegmentData> segments = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             XWPFDocument document = new XWPFDocument(is)) {

            // Extract paragraphs
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    fullText.append(text).append("\n");
                }
            }

            // Extract tables
            for (XWPFTable table : document.getTables()) {
                fullText.append("\n[表格]\n");
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (int i = 0; i < row.getTableCells().size(); i++) {
                        XWPFTableCell cell = row.getTableCells().get(i);
                        if (i > 0) rowText.append(" | ");
                        rowText.append(cell.getText().trim());
                    }
                    fullText.append(rowText).append("\n");
                }
                fullText.append("[/表格]\n");
            }
        }

        String text = fullText.toString().trim();
        if (!text.isEmpty()) {
            List<String> chunks = splitText(text);
            for (int i = 0; i < chunks.size(); i++) {
                segments.add(new SegmentData(chunks.get(i),
                        "{\"chunk\":" + (i + 1) + ",\"total_chunks\":" + chunks.size() + "}"));
            }
        }

        Log.i(TAG, "DOCX parsed: " + segments.size() + " segments");
        return segments;
    }

    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('。', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int lastSpace = text.lastIndexOf(' ', end);
                int breakPoint = Math.max(Math.max(lastPeriod, lastNewline), lastSpace);

                if (breakPoint > start + CHUNK_SIZE / 2) {
                    end = breakPoint + 1;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end - CHUNK_OVERLAP;
            if (start < 0) start = 0;
            if (start >= end) start = end;
        }

        return chunks;
    }
}

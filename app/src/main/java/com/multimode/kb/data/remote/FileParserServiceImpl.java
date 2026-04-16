package com.multimode.kb.data.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.multimode.kb.domain.service.FileParserService;
import com.multimode.kb.llm.OmniClient;

import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableIterator;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
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
            } else if (mimeType.contains("spreadsheet") || mimeType.contains("xls")
                    || mimeType.contains("excel")) {
                return parseSpreadsheet(uri);
            } else if (mimeType.contains("presentation") || mimeType.contains("pptx")) {
                return parsePptx(uri);
            } else if (mimeType.contains("ms-powerpoint") || isLegacyPpt(mimeType, uri)) {
                return parsePpt(uri);
            } else if (mimeType.contains("officedocument") || mimeType.contains("docx")) {
                return parseDocx(uri);
            } else if (mimeType.contains("msword") || isLegacyDoc(mimeType, uri)) {
                return parseDoc(uri);
            } else {
                throw new UnsupportedOperationException("Unsupported MIME type: " + mimeType);
            }
        });
    }

    /** Check if this is a legacy .ppt file (MIME may be generic on some devices). */
    private boolean isLegacyPpt(String mimeType, Uri uri) {
        String path = uri.getLastPathSegment();
        return mimeType.contains("ppt") && (path != null && path.endsWith(".ppt")
                && !path.endsWith(".pptx"));
    }

    /** Check if this is a legacy .doc file (MIME may be generic on some devices). */
    private boolean isLegacyDoc(String mimeType, Uri uri) {
        String path = uri.getLastPathSegment();
        return mimeType.contains("msword") && (path != null && path.endsWith(".doc")
                && !path.endsWith(".docx"));
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
                // tom_roush PDFRenderer has no close/dispose method; GC handles cleanup
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

    /**
     * Parse legacy .doc (Word 97-2003) using HWPF.
     */
    private List<SegmentData> parseDoc(Uri uri) throws Exception {
        List<SegmentData> segments = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             HWPFDocument document = new HWPFDocument(is)) {

            Range range = document.getRange();
            int numParagraphs = range.numParagraphs();

            for (int i = 0; i < numParagraphs; i++) {
                Paragraph paragraph = range.getParagraph(i);
                String text = paragraph.text().trim();
                if (!text.isEmpty()) {
                    fullText.append(text).append("\n");
                }
            }

            // Extract tables using TableIterator
            TableIterator tableIter = new TableIterator(range);
            while (tableIter.hasNext()) {
                Table table = tableIter.next();
                fullText.append("\n[表格]\n");
                for (int r = 0; r < table.numRows(); r++) {
                    TableRow row = table.getRow(r);
                    StringBuilder rowText = new StringBuilder();
                    for (int c = 0; c < row.numCells(); c++) {
                        if (c > 0) rowText.append(" | ");
                        rowText.append(row.getCell(c).text().trim().replace("\n", " "));
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
                        "{\"chunk\":" + (i + 1) + ",\"total_chunks\":" + chunks.size()
                                + ",\"format\":\"doc\"}"));
            }
        }

        Log.i(TAG, "DOC parsed: " + segments.size() + " segments");
        return segments;
    }

    /**
     * Parse XLS/XLSX (Excel) using WorkbookFactory which auto-detects format.
     * Each sheet is extracted with its name as a header.
     */
    private List<SegmentData> parseSpreadsheet(Uri uri) throws Exception {
        List<SegmentData> segments = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             Workbook workbook = WorkbookFactory.create(is)) {

            int numberOfSheets = workbook.getNumberOfSheets();
            Log.i(TAG, "Spreadsheet loaded: " + numberOfSheets + " sheets");

            for (int s = 0; s < numberOfSheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                StringBuilder sheetText = new StringBuilder();
                sheetText.append("[工作表: ").append(sheetName).append("]\n");

                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    boolean hasContent = false;
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (c > 0) rowText.append(" | ");
                        String cellValue = getCellText(cell);
                        if (!cellValue.isEmpty()) hasContent = true;
                        rowText.append(cellValue);
                    }
                    if (hasContent) {
                        sheetText.append(rowText).append("\n");
                    }
                }

                String text = sheetText.toString().trim();
                if (text.length() > sheetName.length() + 20) { // more than just the header
                    List<String> chunks = splitText(text);
                    for (int i = 0; i < chunks.size(); i++) {
                        segments.add(new SegmentData(chunks.get(i),
                                "{\"sheet\":\"" + sheetName.replace("\"", "'")
                                        + "\",\"sheet_index\":" + (s + 1)
                                        + ",\"total_sheets\":" + numberOfSheets
                                        + ",\"chunk\":" + (i + 1) + "}"));
                    }
                }
            }

            Log.i(TAG, "Spreadsheet parsed: " + numberOfSheets + " sheets, "
                    + segments.size() + " segments");
        }
        return segments;
    }

    private String getCellText(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    /**
     * Parse legacy .ppt (PowerPoint 97-2003) using HSLF.
     */
    private List<SegmentData> parsePpt(Uri uri) throws Exception {
        List<SegmentData> segments = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             HSLFSlideShow slideshow = new HSLFSlideShow(is)) {

            List<HSLFSlide> slides = slideshow.getSlides();
            int totalSlides = slides.size();
            Log.i(TAG, "PPT loaded: " + totalSlides + " slides");

            for (int i = 0; i < slides.size(); i++) {
                HSLFSlide slide = slides.get(i);
                StringBuilder slideText = new StringBuilder();

                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        HSLFTextShape textShape = (HSLFTextShape) shape;
                        String text = textShape.getText().trim();
                        if (!text.isEmpty()) {
                            slideText.append(text).append("\n");
                        }
                    }
                }

                String text = slideText.toString().trim();
                if (text.isEmpty()) continue;

                int slideNum = i + 1;
                if (text.length() <= CHUNK_SIZE * 2) {
                    segments.add(new SegmentData(text,
                            "{\"slide\":" + slideNum + ",\"total_slides\":" + totalSlides
                                    + ",\"format\":\"ppt\"}"));
                } else {
                    List<String> chunks = splitText(text);
                    for (int j = 0; j < chunks.size(); j++) {
                        segments.add(new SegmentData(chunks.get(j),
                                "{\"slide\":" + slideNum + ",\"total_slides\":" + totalSlides
                                        + ",\"chunk\":" + (j + 1) + ",\"format\":\"ppt\"}"));
                    }
                }
            }

            Log.i(TAG, "PPT parsed: " + totalSlides + " slides, " + segments.size() + " segments");
        }
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

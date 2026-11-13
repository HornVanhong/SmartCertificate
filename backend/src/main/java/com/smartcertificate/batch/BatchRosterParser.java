package com.smartcertificate.batch;

import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class BatchRosterParser {

    static final int MAX_ROWS_PER_BATCH = 500;
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_COLUMNS = 50;

    record ParsedRoster(
            String normalizedFileName,
            String mimeType,
            byte[] rawBytes,
            List<String> headers,
            List<Map<String, String>> rows) {}

    ParsedRoster parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Upload a non-empty CSV or XLSX roster file.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Roster file must not exceed 5 MB.");
        }

        String originalName = file.getOriginalFilename() == null
                ? "roster.csv"
                : file.getOriginalFilename().trim();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unable to read uploaded roster file.");
        }

        if (lowerName.endsWith(".csv")) {
            return parseCsv(originalName, bytes);
        }
        if (lowerName.endsWith(".xlsx")) {
            return parseXlsx(originalName, bytes);
        }
        throw new AppException(
                ErrorCode.VALIDATION_ERROR, "Only .csv and .xlsx files are supported for bulk certificate generation.");
    }

    private ParsedRoster parseCsv(String fileName, byte[] rawBytes) {
        String content = new String(rawBytes, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }

        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(
                        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                CSVParser parser = new CSVParser(reader, format)) {
            List<String> headers = validateHeaders(parser.getHeaderNames());
            List<Map<String, String>> rows = new ArrayList<>();

            for (CSVRecord record : parser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasAnyValue = false;
                for (String header : headers) {
                    String val = record.isMapped(header) ? cleanCellValue(record.get(header)) : "";
                    if (!val.isEmpty()) {
                        hasAnyValue = true;
                    }
                    rowMap.put(header, val);
                }
                if (!hasAnyValue) {
                    continue;
                }
                rows.add(rowMap);
                if (rows.size() > MAX_ROWS_PER_BATCH) {
                    throw new AppException(
                            ErrorCode.VALIDATION_ERROR,
                            "Bulk import currently supports up to " + MAX_ROWS_PER_BATCH + " rows per batch.");
                }
            }

            if (rows.isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The uploaded CSV contains no data rows.");
            }
            return new ParsedRoster(fileName, "text/csv", rawBytes, headers, rows);
        } catch (AppException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid CSV structure: " + ex.getMessage());
        }
    }

    private ParsedRoster parseXlsx(String fileName, byte[] rawBytes) {
        // Protect against zip-bomb / malformed compressed OOXML entries before parsing.
        ZipSecureFile.setMinInflateRatio(0.01d);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(rawBytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The uploaded XLSX workbook contains no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The first worksheet could not be read.");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row headerRow = findFirstNonBlankRow(sheet, formatter, evaluator);
            if (headerRow == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The uploaded XLSX worksheet is empty.");
            }

            short lastCell = headerRow.getLastCellNum();
            if (lastCell <= 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The XLSX header row contains no columns.");
            }

            List<String> rawHeaders = new ArrayList<>();
            List<Integer> columnIndexes = new ArrayList<>();
            for (int col = 0; col < lastCell; col++) {
                String headerVal = formatCell(headerRow.getCell(col), formatter, evaluator);
                if (!headerVal.isEmpty()) {
                    rawHeaders.add(headerVal);
                    columnIndexes.add(col);
                }
            }
            List<String> headers = validateHeaders(rawHeaders);

            List<Map<String, String>> rows = new ArrayList<>();
            int firstDataRowIndex = headerRow.getRowNum() + 1;
            int lastRowIndex = sheet.getLastRowNum();

            for (int r = firstDataRowIndex; r <= lastRowIndex; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasAnyValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String val = formatCell(row.getCell(columnIndexes.get(i)), formatter, evaluator);
                    if (!val.isEmpty()) {
                        hasAnyValue = true;
                    }
                    rowMap.put(headers.get(i), val);
                }
                if (!hasAnyValue) continue;

                rows.add(rowMap);
                if (rows.size() > MAX_ROWS_PER_BATCH) {
                    throw new AppException(
                            ErrorCode.VALIDATION_ERROR,
                            "Bulk import currently supports up to " + MAX_ROWS_PER_BATCH + " rows per batch.");
                }
            }

            if (rows.isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The uploaded XLSX contains no data rows.");
            }
            return new ParsedRoster(
                    fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    rawBytes,
                    headers,
                    rows);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unable to parse XLSX workbook.");
        }
    }

    private static Row findFirstNonBlankRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int lastRow = sheet.getLastRowNum();
        for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            short lastCell = row.getLastCellNum();
            for (int c = 0; c < lastCell; c++) {
                if (!formatCell(row.getCell(c), formatter, evaluator).isEmpty()) {
                    return row;
                }
            }
        }
        return null;
    }

    private static String formatCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        return cleanCellValue(formatter.formatCellValue(cell, evaluator));
    }

    private static String cleanCellValue(String value) {
        if (value == null) return "";
        return value.trim();
    }

    private static List<String> validateHeaders(List<String> rawHeaders) {
        if (rawHeaders == null || rawHeaders.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Roster file must include a header row.");
        }
        if (rawHeaders.size() > MAX_COLUMNS) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR, "Roster file exceeds maximum column limit (" + MAX_COLUMNS + ").");
        }
        List<String> cleaned = new ArrayList<>(rawHeaders.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : rawHeaders) {
            String header = raw == null ? "" : raw.trim();
            if (header.isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Roster file contains a blank column header.");
            }
            String lower = header.toLowerCase(Locale.ROOT);
            if (!seen.add(lower)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Duplicate column header found: '" + header + "'.");
            }
            cleaned.add(header);
        }
        return cleaned;
    }
}

package com.myfinancemanager.integration.statement;

import com.myfinancemanager.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts raw text from PDF, Excel and CSV bank/credit-card statements. This is the input
 * to the OpenRouter statement parser: the AI reads the text this class produces.
 */
@Slf4j
@Component
public class StatementTextExtractor {

    private static final int MAX_ROWS = 5000;

    public String extract(Path file, String contentType, String originalFilename) {
        String extension = extensionOf(originalFilename);
        try {
            return switch (extension) {
                case "pdf" -> extractPdf(file);
                case "xlsx", "xls" -> extractExcel(file);
                case "csv", "tsv", "txt" -> extractDelimited(file);
                default -> throw new BadRequestException("Unsupported statement format: " + extension);
            };
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Statement text extraction failed for {}: {}", originalFilename, ex.getMessage());
            throw new BadRequestException("Unable to read the uploaded statement file");
        }
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractExcel(Path file) throws IOException {
        StringBuilder builder = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount++ >= MAX_ROWS) {
                        break;
                    }
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    builder.append(String.join(" | ", cells)).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String extractDelimited(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder();
        try (Reader reader = new StringReader(content)) {
            int rows = 0;
            for (CSVRecord record : CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).build().parse(reader)) {
                if (rows++ >= MAX_ROWS) {
                    break;
                }
                List<String> values = new ArrayList<>();
                record.forEach(values::add);
                builder.append(String.join(" | ", values)).append('\n');
            }
        } catch (IOException ex) {
            return content;
        }
        return builder.length() > 0 ? builder.toString() : content;
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BadRequestException("Unable to determine statement file type");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}

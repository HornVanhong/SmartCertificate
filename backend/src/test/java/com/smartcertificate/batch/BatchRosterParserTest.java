package com.smartcertificate.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcertificate.common.exception.AppException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BatchRosterParserTest {

    private final BatchRosterParser parser = new BatchRosterParser();

    @Test
    void parsesUtf8BomCsvAndSkipsEmptyLines() {
        String csv = "\uFEFFrecipientName,courseName,recipientEmail,completionDate\n"
                + "Sokha Dara,Cloud Security,sokha@org.test,2026-11-10\n"
                + ",,,\n"
                + "\"Rivera, Alex\",Zero Trust,alex@org.test,2026-11-11\n";

        MockMultipartFile file =
                new MockMultipartFile("file", "roster.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        BatchRosterParser.ParsedRoster result = parser.parse(file);

        assertThat(result.headers()).containsExactly("recipientName", "courseName", "recipientEmail", "completionDate");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).get("recipientName")).isEqualTo("Sokha Dara");
        assertThat(result.rows().get(1).get("recipientName")).isEqualTo("Rivera, Alex");
    }

    @Test
    void parsesXlsxWorkbookSuccessfully() throws Exception {
        byte[] xlsxBytes;
        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Roster");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("recipientName");
            header.createCell(1).setCellValue("courseName");
            header.createCell(2).setCellValue("completionDate");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Chanthou Kim");
            row1.createCell(1).setCellValue("Data Engineering");
            row1.createCell(2).setCellValue("2026-11-12");

            wb.write(baos);
            xlsxBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "learners.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        BatchRosterParser.ParsedRoster result = parser.parse(file);

        assertThat(result.headers()).containsExactly("recipientName", "courseName", "completionDate");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("recipientName")).isEqualTo("Chanthou Kim");
        assertThat(result.rows().get(0).get("courseName")).isEqualTo("Data Engineering");
    }

    @Test
    void rejectsUnsupportedExtensionDuplicateHeadersAndOversizedRowCounts() {
        MockMultipartFile badExt = new MockMultipartFile(
                "file", "roster.txt", "text/plain", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(badExt)).isInstanceOf(AppException.class);

        MockMultipartFile duplicateHeader = new MockMultipartFile(
                "file", "dup.csv", "text/csv", "recipientName,RecipientName\nA,B\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(duplicateHeader))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Duplicate column header");

        StringBuilder tooMany = new StringBuilder("recipientName,courseName\n");
        for (int i = 0; i < 501; i++) {
            tooMany.append("Learner ").append(i).append(",Course\n");
        }
        MockMultipartFile oversizedRows = new MockMultipartFile(
                "file", "many.csv", "text/csv", tooMany.toString().getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(oversizedRows))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("500");
    }
}

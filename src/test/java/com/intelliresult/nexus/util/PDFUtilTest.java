package com.intelliresult.nexus.util;

import com.intelliresult.nexus.service.dto.MarksheetData;
import com.intelliresult.nexus.service.dto.MarksheetSubjectRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17's "PDF tests". MarksheetData's own Javadoc calls out that it
 * exists specifically to make PDFUtil "trivially unit-testable with a
 * hand-built record instead of a mocked Session" - this is that test. No
 * Hibernate, no IntegrationTestSupport: PDFUtil never touches a Session.
 */
class PDFUtilTest {

    private static final byte[] PDF_MAGIC_BYTES = {'%', 'P', 'D', 'F'};

    private MarksheetSubjectRow subjectWithAllComponents() {
        return new MarksheetSubjectRow("CS101", "Data Structures", new BigDecimal("4.0"),
                new BigDecimal("56.00"), new BigDecimal("20.00"), new BigDecimal("8.00"),
                new BigDecimal("84.00"), new BigDecimal("100.00"),
                new BigDecimal("84.00"), "A", new BigDecimal("9.0"));
    }

    private MarksheetData buildData(List<MarksheetSubjectRow> subjects) {
        return new MarksheetData(
                "IntelliResult Institute of Technology", "123 College Road, Cuttack", null,
                "Dr. Registrar", "Registrar",
                "Test Student", "R2025001", "B.Tech CSE", "Computer Science",
                "A", "Semester 1", "2025-2026",
                "Final Examination", "Final", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20),
                subjects,
                new BigDecimal("84.00"), new BigDecimal("100.00"), new BigDecimal("84.00"),
                "A", new BigDecimal("9.0"), new BigDecimal("8.7"), true,
                3, 12,
                "PUBLISHED",
                "abc123token", realQrPngBytes(), "https://intelliresult.example.edu/verify/result/abc123token",
                LocalDateTime.of(2026, 1, 25, 10, 0));
    }

    /**
     * addVerificationBlock() passes this straight to iText's Image.getInstance(byte[]),
     * which validates real image-format bytes rather than merely checking for a non-null
     * array - an earlier draft of this fixture used new byte[0] here, which is exactly
     * the "not a recognized image format" input that method is right to reject, and
     * failed for that reason rather than any defect in PDFUtil. A real QR PNG (generated
     * through the same QRCodeUtil the production marksheet path actually uses) is both
     * the fix and a more representative fixture than any placeholder image would be.
     */
    private static byte[] realQrPngBytes() {
        try {
            return QRCodeUtil.generatePngBytes("https://intelliresult.example.edu/verify/result/abc123token", 200);
        } catch (Exception e) {
            throw new IllegalStateException("Test fixture setup failed generating a QR image.", e);
        }
    }

    @Test
    void buildMarksheetProducesAStructurallyValidPdf() throws Exception {
        byte[] pdf = PDFUtil.buildMarksheet(buildData(List.of(subjectWithAllComponents())));

        assertTrue(pdf.length > 1000, "A one-subject marksheet should produce more than a trivial number of bytes.");
        byte[] header = new byte[4];
        System.arraycopy(pdf, 0, header, 0, 4);
        assertArrayEquals(PDF_MAGIC_BYTES, header, "Output must start with the standard PDF file signature.");
    }

    @Test
    void aSubjectMissingAnOptionalMarkComponentDoesNotBreakGeneration() {
        // Sec.: "Any of the three mark components may be null... PDFUtil
        // renders a null component as an em-dash" - a subject with no
        // practical component (a theory-only subject) is the normal case
        // this describes, not an edge case, so it must never throw.
        MarksheetSubjectRow theoryOnly = new MarksheetSubjectRow("CS201", "Theory of Computation",
                new BigDecimal("3.0"), new BigDecimal("62.00"), null, new BigDecimal("9.00"),
                new BigDecimal("71.00"), new BigDecimal("80.00"),
                new BigDecimal("88.75"), "A+", new BigDecimal("10.0"));

        assertDoesNotThrow(() -> PDFUtil.buildMarksheet(buildData(List.of(theoryOnly))));
    }

    @Test
    void multipleSubjectsAndAFullSemesterLoadAllRenderWithoutError() {
        List<MarksheetSubjectRow> subjects = List.of(
                subjectWithAllComponents(),
                new MarksheetSubjectRow("CS102", "Discrete Mathematics", new BigDecimal("4.0"),
                        new BigDecimal("48.00"), null, new BigDecimal("7.00"),
                        new BigDecimal("55.00"), new BigDecimal("70.00"),
                        new BigDecimal("78.57"), "B+", new BigDecimal("8.0")),
                new MarksheetSubjectRow("CS103L", "Data Structures Lab", new BigDecimal("2.0"),
                        null, new BigDecimal("47.00"), new BigDecimal("3.00"),
                        new BigDecimal("50.00"), new BigDecimal("50.00"),
                        new BigDecimal("100.00"), "A+", new BigDecimal("10.0"))
        );

        byte[] pdf = assertDoesNotThrow(() -> PDFUtil.buildMarksheet(buildData(subjects)));
        assertTrue(pdf.length > 1000);
    }
}

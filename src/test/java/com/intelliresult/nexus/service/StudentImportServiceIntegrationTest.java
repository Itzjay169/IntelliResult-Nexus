package com.intelliresult.nexus.service;

import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.DataImportException;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 18. This test exists specifically because of a real regression found
 * while writing it: StudentImportService used to reuse UserService.createUser
 * for account creation, and that method was correctly narrowed to only ever
 * accept UserRole.ADMIN when "Admin can no longer create Students/Teachers"
 * was implemented - which meant every bulk student import failed on its
 * very first row. See StudentImportService's own class Javadoc for the fix.
 * This test is what makes that regression impossible to reintroduce silently.
 */
class StudentImportServiceIntegrationTest extends IntegrationTestSupport {

    private final StudentImportService studentImportService = new StudentImportService();

    private Course seedCourse() {
        return seed(() -> {
            Department dept = new Department("Electronics", unique("EC"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech ECE", unique("BTECE"), 8);
            session().persist(course);
            return course;
        });
    }

    private InputStream buildWorkbook(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Students");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Roll No");
            header.createCell(1).setCellValue("Full Name");
            header.createCell(2).setCellValue("Email");
            header.createCell(3).setCellValue("Phone");
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < rows[i].length; c++) {
                    if (rows[i][c] != null) {
                        row.createCell(c).setCellValue(rows[i][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void importingAValidFileActuallyCreatesEveryStudentAccount() throws Exception {
        Course course = seedCourse();
        String roll1 = unique("R");
        String roll2 = unique("R");
        InputStream file = buildWorkbook(new String[][]{
                {roll1, "Alice Example", roll1 + "@example.edu", "9990001111"},
                {roll2, "Bob Example", roll2 + "@example.edu", "9990002222"}
        });

        int created = studentImportService.importStudents(file, course.getId(), null, null);

        assertEquals(2, created);
        session().clear();
        User createdUser = session().createQuery(
                        "FROM User WHERE email = :email", User.class)
                .setParameter("email", roll1 + "@example.edu")
                .uniqueResult();
        assertTrue(createdUser != null && createdUser.getId() != null,
                "This is the exact regression: the account must actually be persisted, " +
                        "not rejected by the now-admin-only createUser() this class used to call.");
    }

    @Test
    void aFileWithOneInvalidRowImportsNothingAtAll() throws Exception {
        Course course = seedCourse();
        String validRoll = unique("R");
        InputStream file = buildWorkbook(new String[][]{
                {validRoll, "Valid Student", validRoll + "@example.edu", "9990003333"},
                {unique("R"), "Missing Email Student", null, "9990004444"}
        });

        DataImportException ex = assertThrows(DataImportException.class,
                () -> studentImportService.importStudents(file, course.getId(), null, null));
        assertTrue(ex.getRowErrors().stream().anyMatch(e -> e.contains("required")));

        session().clear();
        Long count = session().createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", validRoll + "@example.edu")
                .getSingleResult();
        assertEquals(0L, count, "All-or-nothing: the valid row must not be created when another row fails.");
    }
}

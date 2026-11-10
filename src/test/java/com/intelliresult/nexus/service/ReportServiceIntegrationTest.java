package com.intelliresult.nexus.service;

import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ExamType;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.service.dto.ReportData;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 18. ReportService was one of the three services flagged as
 * untested in the earlier Phase 17 pass (docs/FUTURE_SCOPE_AND_LIMITATIONS.md).
 * Covers the shape every report renderer (PDFUtil, ExcelUtil, report-view.jsp)
 * depends on, and the ranking correctness of the Topper Report specifically.
 */
class ReportServiceIntegrationTest extends IntegrationTestSupport {

    private final ReportService reportService = new ReportService();

    private record Fixture(Exam exam, Section section, Student topStudent, Student secondStudent) {}

    private Fixture seedTwoPublishedResults() {
        return seed(() -> {
            Department dept = new Department("Chemistry", unique("CH"));
            session().persist(dept);
            Course course = new Course(dept, "B.Sc Chemistry", unique("BSCCH"), 6);
            session().persist(course);
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Section section = new Section(semester, "A");
            session().persist(section);
            // 70 + 20 + 10 = 100 marks, so a student's marks total is also their percentage.
            Subject subject = new Subject(semester, dept, unique("CH101"), "Organic Chemistry");
            subject.setCredits(new BigDecimal("4.0"));
            subject.setTheoryComponent(new BigDecimal("70"), new BigDecimal("28"));
            subject.setPracticalComponent(new BigDecimal("20"), new BigDecimal("8"));
            subject.setInternalComponent(new BigDecimal("10"), new BigDecimal("4"));
            session().persist(subject);
            Exam exam = new Exam("Final Examination", ExamType.FINAL_EXAMINATION, year, semester,
                    LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(4), 1);
            session().persist(exam);
            User adminUser = new User(unique("admin") + "@example.edu", "hash", "Admin", UserRole.ADMIN);
            session().persist(adminUser);
            User teacherUser = new User(unique("tch") + "@example.edu", "hash", "Teacher", UserRole.TEACHER);
            session().persist(teacherUser);

            Student topStudent = createPublishedResult(course, section, subject, exam, teacherUser, adminUser,
                    new BigDecimal("69.00"), new BigDecimal("20.00"), new BigDecimal("10.00")); // 99/100
            Student secondStudent = createPublishedResult(course, section, subject, exam, teacherUser, adminUser,
                    new BigDecimal("40.00"), new BigDecimal("16.00"), new BigDecimal("8.00")); // 64/100

            return new Fixture(exam, section, topStudent, secondStudent);
        });
    }

    // Persists one student with a published Result and the ResultSummary that ResultCalculationService would
    // have stored for it: every report query reads summaries, so Results alone would produce an empty report.
    private Student createPublishedResult(Course course, Section section, Subject subject, Exam exam,
                                           User teacherUser, User adminUser,
                                           BigDecimal theory, BigDecimal practical, BigDecimal internal) {
        User studentUser = new User(unique("stu") + "@example.edu", "hash", "Test Student", UserRole.STUDENT);
        session().persist(studentUser);
        Student student = new Student(studentUser, unique("R"), course);
        student.setCurrentSection(section);
        session().persist(student);

        BigDecimal total = theory.add(practical).add(internal);
        Result result = new Result(student, exam, subject);
        result.setTheoryMarks(theory);
        result.setPracticalMarks(practical);
        result.setInternalMarks(internal);
        result.markSubmitted(teacherUser);
        result.markApproved(adminUser);
        result.markPublished();
        result.applyCalculatedScore(total, total, "A", new BigDecimal("9.0"), true);
        session().persist(result);

        ResultSummary summary = new ResultSummary(student, exam);
        summary.applyComputedResult(1, 1, total, new BigDecimal("100.00"), new BigDecimal("4.0"),
                total, "A", new BigDecimal("9.0"), new BigDecimal("9.0"), true);
        session().persist(summary);
        return student;
    }

    @Test
    void classResultReportHasOneRowPerStudentInTheSection() {
        Fixture f = seedTwoPublishedResults();

        ReportData report = reportService.classResultReport(f.exam().getId(), f.section().getId());

        assertEquals(2, report.rows().size(), "One row per student who sat this exam in this section.");
        assertTrue(report.columnHeaders().length > 0);
    }

    @Test
    void topperReportOrdersHighestPercentageFirst() {
        Fixture f = seedTwoPublishedResults();

        ReportData report = reportService.topperReport(f.exam().getId(), f.section().getId(), 10);

        assertEquals(2, report.rows().size());
        // ReportData is deliberately column-agnostic (see its own Javadoc),
        // so this checks row order directly rather than assuming which
        // column index holds percentage: the 99/100 student's roll number
        // must appear in row 0, not row 1.
        String firstRowText = java.util.Arrays.toString(report.rows().get(0));
        assertTrue(firstRowText.contains(f.topStudent().getRollNo()),
                "The topper (99/100) must be ranked above the second student (64/100) in row 0.");
    }
}

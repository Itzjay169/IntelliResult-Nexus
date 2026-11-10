package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.GradingRule;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ExamStatus;
import com.intelliresult.nexus.entity.enums.ExamType;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.service.dto.StudentMarksInput;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the result lifecycle and rank recalculation persistence bug.
 */
class ResultServiceIntegrationTest extends IntegrationTestSupport {

    private final ResultService resultService = new ResultService();
    private final MarksEntryService marksEntryService = new MarksEntryService();

    private void persistGradingRules(AcademicYear year) {
        String[][] bands = {
                {"0.00", "39.99", "F", "0.00"},
                {"40.00", "49.99", "D", "5.00"},
                {"50.00", "59.99", "C", "6.00"},
                {"60.00", "69.99", "B", "7.00"},
                {"70.00", "84.99", "A", "8.00"},
                {"85.00", "100.00", "O", "10.00"}
        };
        for (String[] band : bands) {
            session().persist(new GradingRule(year, new BigDecimal(band[0]), new BigDecimal(band[1]),
                    band[2], new BigDecimal(band[3])));
        }
    }

    @Test
    void publishExam_correctMarks_persistsUpdatedPercentageAndRanksInNewSession() {
        record Fixture(Exam exam, Student s1, Student s2, Result r1, Result r2, User admin) {}

        Fixture f = seed(() -> {
            Department dept = new Department("Computer Science", unique("CS"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech CSE", unique("BTCSE"), 8);
            session().persist(course);
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            persistGradingRules(year);

            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Section section = new Section(semester, "A");
            session().persist(section);

            Subject subject = new Subject(semester, dept, unique("CS101"), "Data Structures");
            subject.setCredits(new BigDecimal("4.0"));
            subject.setTheoryComponent(new BigDecimal("70"), new BigDecimal("28"));
            subject.setPracticalComponent(new BigDecimal("20"), new BigDecimal("8"));
            subject.setInternalComponent(new BigDecimal("10"), new BigDecimal("4"));
            session().persist(subject);

            Exam exam = new Exam("Final Examination", ExamType.FINAL_EXAMINATION, year, semester,
                    LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(9), 1);
            exam.setStatus(ExamStatus.APPROVAL);
            session().persist(exam);

            User adminUser = new User(unique("admin") + "@example.edu", "hash", "Admin User", UserRole.ADMIN);
            session().persist(adminUser);
            User teacherUser = new User(unique("tch") + "@example.edu", "hash", "Teacher User", UserRole.TEACHER);
            session().persist(teacherUser);

            User u1 = new User(unique("s1") + "@example.edu", "hash", "Student One", UserRole.STUDENT);
            session().persist(u1);
            Student s1 = new Student(u1, unique("R1"), course);
            s1.setCurrentSection(section);
            session().persist(s1);

            User u2 = new User(unique("s2") + "@example.edu", "hash", "Student Two", UserRole.STUDENT);
            session().persist(u2);
            Student s2 = new Student(u2, unique("R2"), course);
            s2.setCurrentSection(section);
            session().persist(s2);

            // Student 1 starts with 90 marks (90%)
            Result r1 = new Result(s1, exam, subject);
            r1.setTheoryMarks(new BigDecimal("60.00"));
            r1.setPracticalMarks(new BigDecimal("20.00"));
            r1.setInternalMarks(new BigDecimal("10.00"));
            r1.markSubmitted(teacherUser);
            session().persist(r1);

            // Student 2 starts with 60 marks (60%)
            Result r2 = new Result(s2, exam, subject);
            r2.setTheoryMarks(new BigDecimal("40.00"));
            r2.setPracticalMarks(new BigDecimal("12.00"));
            r2.setInternalMarks(new BigDecimal("8.00"));
            r2.markSubmitted(teacherUser);
            session().persist(r2);

            return new Fixture(exam, s1, s2, r1, r2, adminUser);
        });

        // 1. Approve results (calculates subject scores) and publish the exam
        resultService.approveResults(List.of(f.r1().getId(), f.r2().getId()), f.admin());
        List<ResultSummary> published = resultService.publishExam(f.exam().getId());
        assertEquals(2, published.size());

        // 2. Correct Student 2's marks to 95 (higher than Student 1's 90)
        resultService.correctResult(f.r2().getId(),
                new BigDecimal("67.00"), new BigDecimal("19.00"), new BigDecimal("9.00"),
                f.admin(), "Re-eval recount verified undercounted marks");

        // 3. Assert in a NEW session that result_summaries.overall_percentage and ranks changed
        try (Session newSession = HibernateUtil.getSessionFactory().openSession()) {
            ResultSummary s1Summary = newSession.createQuery(
                            "FROM ResultSummary WHERE student.id = :sId AND exam.id = :eId", ResultSummary.class)
                    .setParameter("sId", f.s1().getId())
                    .setParameter("eId", f.exam().getId())
                    .getSingleResult();

            ResultSummary s2Summary = newSession.createQuery(
                            "FROM ResultSummary WHERE student.id = :sId AND exam.id = :eId", ResultSummary.class)
                    .setParameter("sId", f.s2().getId())
                    .setParameter("eId", f.exam().getId())
                    .getSingleResult();

            // Student 2 was 60%, now 95%
            assertEquals(0, new BigDecimal("95.00").compareTo(s2Summary.getOverallPercentage()));
            assertEquals(1, s2Summary.getClassRank());
            assertEquals(1, s2Summary.getOverallRank());

            // Student 1 remains 90%, rank moves from 1 to 2
            assertEquals(0, new BigDecimal("90.00").compareTo(s1Summary.getOverallPercentage()));
            assertEquals(2, s1Summary.getClassRank());
            assertEquals(2, s1Summary.getOverallRank());
        }
    }

    @Test
    void fullFlow_draftToSubmittedToApprovedToPublished_persistsAllInNewSession() {
        record FlowFixture(Exam exam, Subject subject, Section section, Teacher teacher, User teacherUser,
                           User adminUser, Student student) {}

        FlowFixture f = seed(() -> {
            Department dept = new Department("Electronics", unique("ECE"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech ECE", unique("BTECE"), 8);
            session().persist(course);
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            persistGradingRules(year);

            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Section section = new Section(semester, "A");
            session().persist(section);

            Subject subject = new Subject(semester, dept, unique("EC101"), "Circuits");
            subject.setCredits(new BigDecimal("3.0"));
            subject.setTheoryComponent(new BigDecimal("70"), new BigDecimal("28"));
            subject.setPracticalComponent(new BigDecimal("20"), new BigDecimal("8"));
            subject.setInternalComponent(new BigDecimal("10"), new BigDecimal("4"));
            session().persist(subject);

            Exam exam = new Exam("Mid Term", ExamType.MID_SEMESTER, year, semester,
                    LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(4), 1);
            exam.setStatus(ExamStatus.ACTIVE); // open for marks entry
            session().persist(exam);

            User adminUser = new User(unique("adm") + "@example.edu", "hash", "Admin", UserRole.ADMIN);
            session().persist(adminUser);

            User teacherUser = new User(unique("tch") + "@example.edu", "hash", "Teacher", UserRole.TEACHER);
            session().persist(teacherUser);
            Teacher teacher = new Teacher(teacherUser, unique("EMP"), dept);
            session().persist(teacher);

            TeacherSubject assignment = new TeacherSubject(teacher, subject, section, adminUser);
            session().persist(assignment);

            User studentUser = new User(unique("stu") + "@example.edu", "hash", "Student", UserRole.STUDENT);
            session().persist(studentUser);
            Student student = new Student(studentUser, unique("R"), course);
            student.setCurrentSection(section);
            session().persist(student);

            return new FlowFixture(exam, subject, section, teacher, teacherUser, adminUser, student);
        });

        // 1. Teacher enters marks and saves DRAFT
        List<StudentMarksInput> inputs = List.of(
                new StudentMarksInput(f.student().getId(), new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("8.00"))
        );
        int savedDraftCount = marksEntryService.saveDraft(
                f.exam().getId(), f.subject().getId(), f.section().getId(), f.teacher().getId(), f.teacherUser(), inputs);
        assertEquals(1, savedDraftCount);

        Result draftResult = session().createQuery(
                        "FROM Result WHERE student.id = :sId AND exam.id = :eId AND subject.id = :subId", Result.class)
                .setParameter("sId", f.student().getId())
                .setParameter("eId", f.exam().getId())
                .setParameter("subId", f.subject().getId())
                .getSingleResult();
        assertEquals(ResultStatus.DRAFT, draftResult.getStatus());

        // 2. Teacher submits the marks
        int submittedCount = marksEntryService.submitGrid(
                f.exam().getId(), f.subject().getId(), f.section().getId(), f.teacher().getId(), f.teacherUser(), inputs);
        assertEquals(1, submittedCount);

        session().refresh(draftResult);
        assertEquals(ResultStatus.SUBMITTED, draftResult.getStatus());

        // 3. Admin approves
        List<Result> approved = resultService.approveResults(List.of(draftResult.getId()), f.adminUser());
        assertEquals(1, approved.size());
        assertEquals(ResultStatus.APPROVED, approved.get(0).getStatus());

        // 4. Admin publishes
        List<ResultSummary> published = resultService.publishExam(f.exam().getId());
        assertEquals(1, published.size());

        // 5. Reload in a completely NEW session and assert persistence
        try (Session newSession = HibernateUtil.getSessionFactory().openSession()) {
            Result persistedResult = newSession.get(Result.class, draftResult.getId());
            assertNotNull(persistedResult);
            assertEquals(ResultStatus.PUBLISHED, persistedResult.getStatus());
            assertEquals(0, new BigDecimal("73.00").compareTo(persistedResult.getTotalMarks()));
            assertEquals(0, new BigDecimal("73.00").compareTo(persistedResult.getPercentage()));
            assertTrue(persistedResult.getPass());

            ResultSummary persistedSummary = newSession.createQuery(
                            "FROM ResultSummary WHERE student.id = :sId AND exam.id = :eId", ResultSummary.class)
                    .setParameter("sId", f.student().getId())
                    .setParameter("eId", f.exam().getId())
                    .getSingleResult();
            assertNotNull(persistedSummary);
            assertEquals(0, new BigDecimal("73.00").compareTo(persistedSummary.getOverallPercentage()));
            assertNotNull(persistedSummary.getClassRank());
            assertNotNull(persistedSummary.getOverallRank());
            assertEquals(1, persistedSummary.getClassRank());
            assertEquals(1, persistedSummary.getOverallRank());
        }
    }
}

package com.intelliresult.nexus.service;

import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.GradingRule;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ExamType;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 17. Exercises the exact chain the brief called "currently broken -
 * fix completely": student submits, admin assigns to any teacher, that
 * teacher resolves with new marks, marks change only on an approved
 * resolution that actually differs, and a ResultHistory row is created
 * automatically. RevaluationService itself already documents this as a
 * rebuild (the delegation step didn't exist before) - this test is what
 * locks that fix in place against a future regression.
 */
class RevaluationServiceIntegrationTest extends IntegrationTestSupport {

    private final RevaluationService revaluationService = new RevaluationService();

    private record Fixture(Student student, Teacher teacher, Teacher otherTeacher, User admin, Result publishedResult) {}

    // A covering rule set for the year: an approved correction recalculates the subject result, and
    // ResultCalculationService throws when no active GradingRule covers the percentage. Production gets
    // these rows from schema.sql; the fresh in-memory test schema starts empty.
    private void persistGradingRules(AcademicYear year) {
        String[][] bands = {
                {"0.00", "39.99", "F", "0.00"}, {"40.00", "49.99", "D", "5.00"}, {"50.00", "59.99", "C", "6.00"},
                {"60.00", "69.99", "B", "7.00"}, {"70.00", "84.99", "A", "8.00"}, {"85.00", "100.00", "O", "10.00"}
        };
        for (String[] band : bands) {
            session().persist(new GradingRule(year, new BigDecimal(band[0]), new BigDecimal(band[1]),
                    band[2], new BigDecimal(band[3])));
        }
    }

    private Fixture seedPublishedResult() {
        return seed(() -> {
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
            // All three components (70 + 20 + 10 = 100): marks entered for a component the subject lacks are rejected.
            Subject subject = new Subject(semester, dept, unique("CS101"), "Data Structures");
            subject.setCredits(new BigDecimal("4.0"));
            subject.setTheoryComponent(new BigDecimal("70"), new BigDecimal("28"));
            subject.setPracticalComponent(new BigDecimal("20"), new BigDecimal("8"));
            subject.setInternalComponent(new BigDecimal("10"), new BigDecimal("4"));
            session().persist(subject);
            Exam exam = new Exam("Final Examination", ExamType.FINAL_EXAMINATION, year, semester,
                    LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(9), 1);
            session().persist(exam);

            User studentUser = new User(unique("stu") + "@example.edu", "hash", "Test Student", UserRole.STUDENT);
            session().persist(studentUser);
            Student student = new Student(studentUser, unique("R"), course);
            student.setCurrentSection(section);
            session().persist(student);

            User teacherUser = new User(unique("tch") + "@example.edu", "hash", "Assigned Teacher", UserRole.TEACHER);
            session().persist(teacherUser);
            Teacher teacher = new Teacher(teacherUser, unique("EMP"), dept);
            session().persist(teacher);

            User otherTeacherUser = new User(unique("tch2") + "@example.edu", "hash", "Other Teacher", UserRole.TEACHER);
            session().persist(otherTeacherUser);
            Teacher otherTeacher = new Teacher(otherTeacherUser, unique("EMP2"), dept);
            session().persist(otherTeacher);

            User admin = new User(unique("admin") + "@example.edu", "hash", "Test Admin", UserRole.ADMIN);
            session().persist(admin);

            Result result = new Result(student, exam, subject);
            result.setTheoryMarks(new BigDecimal("50.00"));
            result.setPracticalMarks(new BigDecimal("20.00"));
            result.setInternalMarks(new BigDecimal("8.00"));
            result.markSubmitted(teacherUser);
            result.markApproved(admin);
            result.markPublished();
            session().persist(result);

            return new Fixture(student, teacher, otherTeacher, admin, result);
        });
    }

    @Test
    void fullLifecycle_approvedWithChangedMarksUpdatesResultAndCreatesHistory() {
        Fixture f = seedPublishedResult();

        RevaluationRequest submitted = revaluationService.submitRequest(
                f.student().getId(), f.publishedResult().getId(), "Theory answer script totals look miscounted.");
        assertEquals(RevaluationStatus.PENDING, submitted.getStatus());

        RevaluationRequest assigned = revaluationService.assignToTeacher(
                submitted.getId(), f.teacher().getId(), "Please recheck theory marks.", f.admin().getId());
        assertEquals(RevaluationStatus.ASSIGNED, assigned.getStatus());

        RevaluationRequest resolved = revaluationService.resolveRequest(
                assigned.getId(), f.teacher().getId(), RevaluationStatus.APPROVED,
                "Re-checked; two questions were undercounted by a total of 6 marks.",
                new BigDecimal("56.00"), new BigDecimal("20.00"), new BigDecimal("8.00"));

        assertEquals(RevaluationStatus.APPROVED, resolved.getStatus());

        session().clear();
        Result reloaded = session().find(Result.class, f.publishedResult().getId());
        assertEquals(0, new BigDecimal("56.00").compareTo(reloaded.getTheoryMarks()),
                "Marks must actually be updated after an approved resolution that changes them.");

        Long historyCount = session().createQuery(
                        "SELECT COUNT(h) FROM ResultHistory h WHERE h.result.id = :resultId", Long.class)
                .setParameter("resultId", f.publishedResult().getId())
                .getSingleResult();
        assertEquals(1L, historyCount, "An approved, marks-changing resolution must create exactly one history row.");
    }

    @Test
    void resolvedButUnchangedMarksCreateNoHistoryRow() {
        Fixture f = seedPublishedResult();
        RevaluationRequest submitted = revaluationService.submitRequest(
                f.student().getId(), f.publishedResult().getId(), "Please double check my total.");
        RevaluationRequest assigned = revaluationService.assignToTeacher(
                submitted.getId(), f.teacher().getId(), null, f.admin().getId());

        // Same values the result already has - re-evaluation confirms the original marks were correct.
        revaluationService.resolveRequest(assigned.getId(), f.teacher().getId(), RevaluationStatus.APPROVED,
                "Recount matches the original marks exactly.",
                new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("8.00"));

        Long historyCount = session().createQuery(
                        "SELECT COUNT(h) FROM ResultHistory h WHERE h.result.id = :resultId", Long.class)
                .setParameter("resultId", f.publishedResult().getId())
                .getSingleResult();
        assertEquals(0L, historyCount,
                "Confirming the same marks is not a correction and must not create a history entry.");
    }

    @Test
    void aTeacherOtherThanTheOneAssignedCannotResolveTheRequest() {
        Fixture f = seedPublishedResult();
        RevaluationRequest submitted = revaluationService.submitRequest(
                f.student().getId(), f.publishedResult().getId(), "Please recheck.");
        RevaluationRequest assigned = revaluationService.assignToTeacher(
                submitted.getId(), f.teacher().getId(), null, f.admin().getId());

        assertThrows(BusinessRuleException.class, () -> revaluationService.resolveRequest(
                assigned.getId(), f.otherTeacher().getId(), RevaluationStatus.APPROVED,
                "Attempting to resolve someone else's assignment.",
                new BigDecimal("60.00"), new BigDecimal("20.00"), new BigDecimal("8.00")));
    }

    @Test
    void submittingARequestForAResultThatWasNeverPublishedIsRejected() {
        Fixture f = seedPublishedResult();
        // Lock the result back down to DRAFT-equivalent territory by using a
        // second, never-published result on the same student/subject shape
        // instead of mutating the fixture's already-published one.
        Result draftResult = seed(() -> {
            Department dept = new Department("Mechanical", unique("ME"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech ME", unique("BTME"), 8);
            session().persist(course);
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Subject subject = new Subject(semester, dept, unique("ME101"), "Thermodynamics");
            subject.setTheoryComponent(new BigDecimal("70"), new BigDecimal("28"));
            session().persist(subject);
            Exam exam = new Exam("Unit Test 1", ExamType.UNIT_TEST, year, semester,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2), 1);
            session().persist(exam);
            Result draft = new Result(f.student(), exam, subject);
            draft.setTheoryMarks(new BigDecimal("40.00"));
            session().persist(draft); // left in DRAFT - never submitted/approved/published
            return draft;
        });

        assertThrows(BusinessRuleException.class, () -> revaluationService.submitRequest(
                f.student().getId(), draftResult.getId(), "Trying to contest a result that was never published."));
    }
}

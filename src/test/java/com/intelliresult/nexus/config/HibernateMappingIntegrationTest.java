package com.intelliresult.nexus.config;

import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17. This is not a test of any one feature - it exists because of a
 * real, observed failure: the bundled application logs from 2026-09-24 show
 * "org.hibernate.query.sqm.UnknownEntityException: Could not resolve root
 * entity 'RegistrationRequest'" and a separate SemanticException from a
 * stale field name, both thrown at *request time*, not at startup. An entity
 * class can compile perfectly and still be silently absent (or present under
 * the wrong name) from the SessionFactory's actual metamodel, and nothing
 * before Phase 17 has ever asserted that the mapped model matches the class
 * list in hibernate.cfg.xml. This test makes that assertion directly - the
 * first thing it would have caught, both times.
 */
class HibernateMappingIntegrationTest extends IntegrationTestSupport {

    /** Exactly the class list in hibernate.cfg.xml (both the main and test copies, which are kept in sync) - see that file's own comment on why they must match. */
    private static final Set<String> EXPECTED_ENTITY_NAMES = Set.of(
            "Department", "Course", "AcademicYear", "Semester", "Section", "Subject",
            "User", "Student", "Teacher", "TeacherSubject", "Exam", "Result",
            "ResultSummary", "ResultHistory", "RevaluationRequest", "GradingRule",
            "Notification", "ActivityLog", "Notice", "SystemSetting",
            "MarksheetVerification", "RegistrationRequest", "OtpCode", "PasswordResetRequest"
    );

    @Test
    void everyExpectedEntityIsResolvableInTheMetamodel() {
        Set<String> actual = HibernateUtil.getSessionFactory().getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        // Symmetric difference in one assertion: fails with the exact missing
        // AND the exact unexpected names, rather than just "not equal".
        Set<String> missing = EXPECTED_ENTITY_NAMES.stream()
                .filter(name -> !actual.contains(name)).collect(Collectors.toSet());
        Set<String> unexpected = actual.stream()
                .filter(name -> !EXPECTED_ENTITY_NAMES.contains(name)).collect(Collectors.toSet());

        assertTrue(missing.isEmpty(),
                () -> "These entities are NOT resolvable via HQL despite being expected: " + missing);
        assertTrue(unexpected.isEmpty(),
                () -> "These entities are mapped but not in the expected list (update this test if intentional): " + unexpected);
        assertEquals(EXPECTED_ENTITY_NAMES.size(), actual.size());
    }

    /**
     * A metamodel name check alone would not catch a wrong column name or a
     * broken @JoinColumn, so this walks one real, representative chain -
     * Department -> Course -> AcademicYear -> Semester -> Section, plus
     * User -> Student - end to end through an actual persist and a fresh
     * reload, the same chain RegistrationService.approveStudent() depends on.
     */
    @Test
    void coreAcademicChainPersistsAndReloadsCorrectly() {
        Student student = seed(() -> {
            Department dept = new Department("Computer Science", unique("CS"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech CSE", unique("BTCSE"), 8);
            session().persist(course);
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Section section = new Section(semester, "A");
            session().persist(section);
            Subject subject = new Subject(semester, dept, unique("CS101"), "Data Structures");
            subject.setCredits(new BigDecimal("4.0"));
            subject.setTheoryComponent(new BigDecimal("70"), new BigDecimal("28"));
            session().persist(subject);

            User user = new User(unique("student") + "@example.edu", "irrelevant-hash", "Test Student", UserRole.STUDENT);
            session().persist(user);
            Student created = new Student(user, unique("R"), course);
            created.setCurrentSection(section);
            session().persist(created);
            return created;
        });
        session().clear(); // Forces the reload below to hit the database, not the first-level cache.

        Student reloaded = session().find(Student.class, student.getId());
        assertEquals(student.getRollNo(), reloaded.getRollNo());
        assertEquals(student.getCourse().getId(), reloaded.getCourse().getId());
        assertEquals(student.getCurrentSection().getId(), reloaded.getCurrentSection().getId());
        assertEquals("Test Student", reloaded.getUser().getFullName());
        assertEquals(8, reloaded.getCourse().getTotalSemesters());
    }
}

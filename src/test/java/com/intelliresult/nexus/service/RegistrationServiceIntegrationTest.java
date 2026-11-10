package com.intelliresult.nexus.service;

import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.RegistrationRequest;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.RegistrationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.StudentRegistrationRequest;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17. Covers the exact invariant AuthenticationService's own login()
 * comment relies on and this project's Sec. 9 requirement states directly:
 * "Only Active users can log in" - which here means only an approved
 * registration ever creates a row in `users` at all, never a status flag
 * that could be forgotten in some other query.
 */
class RegistrationServiceIntegrationTest extends IntegrationTestSupport {

    private final RegistrationService registrationService = new RegistrationService();

    private Course seedCourse() {
        return seed(() -> {
            Department dept = new Department("Computer Science", unique("CS"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech CSE", unique("BTCSE"), 8);
            session().persist(course);
            return course;
        });
    }

    private Section seedSection(Course course) {
        return seed(() -> {
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Section section = new Section(semester, "A");
            session().persist(section);
            return section;
        });
    }

    private Long seedAdmin() {
        return seed(() -> {
            User admin = new User(unique("admin") + "@example.edu", "irrelevant-hash", "Test Admin", UserRole.ADMIN);
            session().persist(admin);
            return admin.getId();
        });
    }

    @Test
    void approvingAStudentRegistrationCreatesTheUserAndStudentRowsTogether() {
        Course course = seedCourse();
        Section section = seedSection(course);
        Long adminId = seedAdmin();
        String rollNo = unique("R");

        RegistrationRequest pending = registrationService.submitStudentRegistration(
                new StudentRegistrationRequest("New Student", rollNo, "9990001111",
                        unique("new.student") + "@example.edu", "a-strong-password-1"));

        assertEquals(RegistrationStatus.PENDING, pending.getStatus());
        assertTrue(registrationService.listPending().stream().anyMatch(r -> r.getId().equals(pending.getId())));

        User created = registrationService.approveStudent(pending.getId(), course.getId(), section.getId(), adminId);

        assertNotNull(created.getId(), "approveStudent() must actually persist the new User.");
        assertEquals(UserRole.STUDENT, created.getRole());
        assertFalse(registrationService.listPending().stream().anyMatch(r -> r.getId().equals(pending.getId())),
                "An approved request must leave the pending queue.");
    }

    @Test
    void rejectingARegistrationNeverCreatesAUserRow() {
        String rollNo = unique("R");
        String email = unique("rejected.student") + "@example.edu";
        Long adminId = seedAdmin();

        RegistrationRequest pending = registrationService.submitStudentRegistration(
                new StudentRegistrationRequest("Rejected Student", rollNo, "9990002222",
                        email, "a-strong-password-1"));

        registrationService.reject(pending.getId(), adminId, "Roll number could not be verified against records.");

        List<RegistrationRequest> reviewed = registrationService.listReviewed();
        RegistrationRequest reviewedEntry = reviewed.stream()
                .filter(r -> r.getId().equals(pending.getId())).findFirst().orElseThrow();
        assertEquals(RegistrationStatus.REJECTED, reviewedEntry.getStatus());

        // The actual invariant under test: rejection must never leave behind
        // a row in `users` for this identifier, since AuthenticationService
        // relies on "no user row exists" (not a status check) to keep a
        // rejected applicant from ever logging in.
        Optional<User> shouldNotExist = session().createQuery(
                        "FROM User WHERE email = :email", User.class)
                .setParameter("email", email)
                .uniqueResultOptional();
        assertTrue(shouldNotExist.isEmpty());
    }

    @Test
    void aSecondRegistrationWithTheSameRollNumberWhileTheFirstIsStillPendingIsRejectedAtSubmission() {
        String rollNo = unique("R");
        registrationService.submitStudentRegistration(
                new StudentRegistrationRequest("First Applicant", rollNo, "9990003333",
                        unique("first") + "@example.edu", "a-strong-password-1"));

        ValidationException ex = assertThrows(ValidationException.class, () -> registrationService.submitStudentRegistration(
                new StudentRegistrationRequest("Second Applicant", rollNo, "9990004444",
                        unique("second") + "@example.edu", "a-different-password-2")),
                "Submitting a second pending registration for a roll number already pending must be rejected, " +
                        "not silently queued as a duplicate.");
        assertTrue(ex.getFieldErrors().containsKey("rollNo"),
                "The rejection must be about the roll number itself, not some other invalid field.");
    }
}

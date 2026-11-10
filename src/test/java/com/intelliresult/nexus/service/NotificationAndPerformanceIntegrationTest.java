package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.NotificationDAO;
import com.intelliresult.nexus.dao.NotificationDAOImpl;
import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.GradingRule;
import com.intelliresult.nexus.entity.Notification;
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
import com.intelliresult.nexus.service.dto.StudentPerformanceRow;
import com.intelliresult.nexus.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationAndPerformanceIntegrationTest extends IntegrationTestSupport {

    private final NotificationService notificationService = new NotificationService();
    private final NotificationDAO notificationDAO = new NotificationDAOImpl();
    private final StudentAnalyticsService analyticsService = new StudentAnalyticsService();
    private final MarksheetService marksheetService = new MarksheetService();
    private final ResultService resultService = new ResultService();

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
    void examCreationAndScheduling_createsNotificationsAndPreventsDuplicates() {
        record Fixture(Exam exam, Student student, Teacher teacher, User admin) {}

        Fixture f = seed(() -> {
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

            Subject subject = new Subject(semester, dept, unique("EC101"), "Digital Logic");
            subject.setCredits(new BigDecimal("4.0"));
            subject.setTheoryComponent(new BigDecimal("100"), new BigDecimal("40"));
            session().persist(subject);

            User studentUser = new User(unique("stu") + "@example.edu", "password", "Student User", UserRole.STUDENT);
            session().persist(studentUser);
            Student student = new Student(studentUser, unique("R2026"), course);
            student.setCurrentSection(section);
            student.setAdmissionYear(year);
            session().persist(student);

            User teacherUser = new User(unique("tch") + "@example.edu", "password", "Teacher User", UserRole.TEACHER);
            session().persist(teacherUser);
            Teacher teacher = new Teacher(teacherUser, unique("EMP"), dept);
            session().persist(teacher);

            User admin = new User(unique("adm") + "@example.edu", "password", "Admin User", UserRole.ADMIN);
            session().persist(admin);

            TeacherSubject ts = new TeacherSubject(teacher, subject, section, admin);
            session().persist(ts);

            Exam exam = new Exam("Mid-Term Exam", ExamType.MID_SEMESTER, year, semester,
                    LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 13, 0), 1);
            session().persist(exam);

            return new Fixture(exam, student, teacher, admin);
        });

        // Trigger exam created
        notificationService.notifyExamCreated(f.exam());

        List<Notification> studentNotifications = notificationDAO.findByUser(f.student().getUser().getId());
        assertFalse(studentNotifications.isEmpty(), "Student should receive exam creation notification");
        assertEquals("EXAM_CREATED", studentNotifications.get(0).getNotificationType());
        assertTrue(studentNotifications.get(0).getMessage().contains("Mid-Term Exam"));

        List<Notification> teacherNotifications = notificationDAO.findByUser(f.teacher().getUser().getId());
        assertFalse(teacherNotifications.isEmpty(), "Teacher should receive exam creation notification");
        assertEquals("EXAM_CREATED", teacherNotifications.get(0).getNotificationType());

        // De-duplication check: re-running does not produce duplicate notifications
        int initialStudentCount = studentNotifications.size();
        notificationService.notifyExamCreated(f.exam());
        List<Notification> afterRetry = notificationDAO.findByUser(f.student().getUser().getId());
        assertEquals(initialStudentCount, afterRetry.size(), "Duplicate notification must not be created");
    }

    @Test
    void publishExam_and_resultUpdated_createsTargetedNotificationsAndMarksheet() {
        record Fixture(Exam exam, Student student, Teacher teacher, User admin, Result result, Subject subject) {}

        Fixture f = seed(() -> {
            Department dept = new Department("Mechanical", unique("ME"));
            session().persist(dept);
            Course course = new Course(dept, "B.Tech ME", unique("BTME"), 8);
            session().persist(course);
            AcademicYear year = new AcademicYear(unique("AY"), LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));
            session().persist(year);
            persistGradingRules(year);

            Semester semester = new Semester(course, year, 1);
            session().persist(semester);
            Section section = new Section(semester, "A");
            session().persist(section);

            Subject subject = new Subject(semester, dept, unique("ME101"), "Thermodynamics");
            subject.setCredits(new BigDecimal("4.0"));
            subject.setTheoryComponent(new BigDecimal("100"), new BigDecimal("40"));
            session().persist(subject);

            User studentUser = new User(unique("stu") + "@example.edu", "password", "Mech Student", UserRole.STUDENT);
            session().persist(studentUser);
            Student student = new Student(studentUser, unique("R2026M"), course);
            student.setCurrentSection(section);
            student.setAdmissionYear(year);
            session().persist(student);

            User teacherUser = new User(unique("tch") + "@example.edu", "password", "Mech Teacher", UserRole.TEACHER);
            session().persist(teacherUser);
            Teacher teacher = new Teacher(teacherUser, unique("EMPME"), dept);
            session().persist(teacher);

            User admin = new User(unique("adm") + "@example.edu", "password", "Admin User", UserRole.ADMIN);
            session().persist(admin);

            TeacherSubject ts = new TeacherSubject(teacher, subject, section, admin);
            session().persist(ts);

            Exam exam = new Exam("Semester 1 Final", ExamType.FINAL_EXAMINATION, year, semester,
                    LocalDateTime.of(2026, 12, 1, 9, 30), LocalDateTime.of(2026, 12, 1, 12, 30), 1);
            exam.setStatus(ExamStatus.APPROVAL);
            session().persist(exam);

            Result r = new Result(student, exam, subject);
            r.setTheoryMarks(new BigDecimal("75.00"));
            r.markSubmitted(teacherUser);
            session().persist(r);

            return new Fixture(exam, student, teacher, admin, r, subject);
        });

        // Approve results (calculates score and sets status to APPROVED)
        resultService.approveResults(List.of(f.result().getId()), f.admin());

        // Publish exam
        List<ResultSummary> summaries = resultService.publishExam(f.exam().getId());
        assertEquals(1, summaries.size());

        // Notify publication
        notificationService.notifyResultPublished(f.student(), f.exam());
        notificationService.notifyResultPublishedToTeachers(f.exam());
        notificationService.notifyResultPublishedToAdmin(f.exam(), f.admin());

        List<Notification> studentNotes = notificationDAO.findByUser(f.student().getUser().getId());
        assertTrue(studentNotes.stream().anyMatch(n -> "RESULT_PUBLISHED".equals(n.getNotificationType())));

        List<Notification> teacherNotes = notificationDAO.findByUser(f.teacher().getUser().getId());
        assertTrue(teacherNotes.stream().anyMatch(n -> "RESULT_PUBLISHED".equals(n.getNotificationType())));

        List<Notification> adminNotes = notificationDAO.findByUser(f.admin().getId());
        assertTrue(adminNotes.stream().anyMatch(n -> "RESULT_PUBLISHED".equals(n.getNotificationType())));

        // Test Marksheet generation for the published student
        byte[] pdf = marksheetService.generateMarksheetPdf(f.student().getId(), f.exam().getId(),
                f.student().getUser(), "http://localhost:8080/intelliresult-nexus");
        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "Marksheet PDF should have valid content");

        // Test Performance overview row
        List<StudentPerformanceRow> rows = analyticsService.buildPerformanceOverview(List.of(f.student()));
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).completedExamsCount());
        assertTrue(rows.get(0).pass());

        // Result correction and notification
        Result corrected = session().find(Result.class, f.result().getId());
        notificationService.notifyResultUpdated(corrected, f.admin(), "Re-evaluation mark revision");
        List<Notification> updatedNotes = notificationDAO.findByUser(f.student().getUser().getId());
        assertTrue(updatedNotes.stream().anyMatch(n -> "RESULT_UPDATED".equals(n.getNotificationType())));
    }
}

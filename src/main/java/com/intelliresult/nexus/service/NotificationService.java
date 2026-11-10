package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.NotificationDAO;
import com.intelliresult.nexus.dao.NotificationDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.Notification;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.AuthorizationException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.util.DateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sec. 37 names this service explicitly. The facade every controller that
 * triggers one of Sec. 23's four events actually calls - always writes
 * the in-app {@link Notification} row first (the reliable channel every
 * user has, badge and all), then best-effort delegates to {@link
 * EmailNotificationService} (the additive channel Sec. 24 adds on top).
 * Never the other way around: an unreachable mail server must not be able
 * to stop a student from finding out their result was published the
 * moment they next log in, even if the email about it never arrives.
 * <p>
 * In-app writes go through the same {@code inTransaction} idiom {@code
 * SystemSettingsService}/{@code ResultService}/{@code RevaluationService}/
 * {@code MarksEntryService} already use for their own core persistence -
 * the one pattern in this codebase with a proven, unambiguous commit
 * story, which matters here specifically because {@link
 * #notifyNoticePublished} can write one row per matching user and a
 * partial fan-out (some recipients notified, others silently not, with no
 * error surfaced) would be a worse outcome than the whole notify failing
 * together and being retried.
 */
public class NotificationService {

    private final NotificationDAO notificationDAO = new NotificationDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final EmailNotificationService emailNotificationService = new EmailNotificationService();

    /** Called once per student whose result just became visible (Sec. 23's "Result published"). */
    public void notifyResultPublished(Student student, Exam exam) {
        User user = student.getUser();
        String dateStr = exam.getStartTime() != null ? DateUtil.formatForDisplay(exam.getStartTime().toLocalDate()) : "";
        String title = "Result Published: " + exam.getName();
        String message = "Results published for " + exam.getName() + (dateStr.isEmpty() ? "" : " on " + dateStr)
                + ". You can now view your result and download your marksheet.";
        String linkUrl = "/student/results?examId=" + exam.getId();

        inTransaction(() -> {
            if (!notificationDAO.existsByRecipientAndEntity(user.getId(), "RESULT_PUBLISHED", "EXAM", exam.getId())) {
                notificationDAO.save(new Notification(user, title, message, "RESULT_PUBLISHED", "EXAM", exam.getId(), linkUrl));
            }
            return null;
        });
        emailNotificationService.sendResultPublished(user, exam);
    }

    /** Notifies all teachers assigned to subjects included in the published exam. */
    public void notifyResultPublishedToTeachers(Exam exam) {
        if (exam.getSemester() == null) return;
        List<TeacherSubject> assignments = teacherSubjectDAO.findActiveBySemester(exam.getSemester().getId());
        Set<Long> notifiedUserIds = new HashSet<>();
        String dateStr = exam.getStartTime() != null ? DateUtil.formatForDisplay(exam.getStartTime().toLocalDate()) : "";
        String title = "Results Published: " + exam.getName();
        String linkUrl = "/teacher/submitted-results?examId=" + exam.getId();

        inTransaction(() -> {
            for (TeacherSubject ts : assignments) {
                User teacherUser = ts.getTeacher().getUser();
                if (notifiedUserIds.add(teacherUser.getId())) {
                    if (!notificationDAO.existsByRecipientAndEntity(teacherUser.getId(), "RESULT_PUBLISHED", "EXAM", exam.getId())) {
                        String message = "Results published for " + exam.getName() + (dateStr.isEmpty() ? "" : " on " + dateStr)
                                + ". Assigned subject: " + ts.getSubject().getSubjectName() + " (" + ts.getSection().getName() + "). You can now view submitted results.";
                        notificationDAO.save(new Notification(teacherUser, title, message, "RESULT_PUBLISHED", "EXAM", exam.getId(), linkUrl));
                    }
                }
            }
            return null;
        });
    }

    /** Notifies the admin that results have been published. */
    public void notifyResultPublishedToAdmin(Exam exam, User admin) {
        if (admin == null) return;
        String title = "Results Published: " + exam.getName();
        String message = "Results for " + exam.getName() + " have been successfully published. Students and assigned teachers have been notified.";
        String linkUrl = "/admin/results?examId=" + exam.getId();

        inTransaction(() -> {
            notificationDAO.save(new Notification(admin, title, message, "RESULT_PUBLISHED", "EXAM", exam.getId(), linkUrl));
            return null;
        });
    }

    /** Notifies student and evaluator when a result is corrected/republished after initial publication. */
    public void notifyResultUpdated(Result result, User updater, String reason) {
        Student student = result.getStudent();
        Exam exam = result.getExam();
        Subject subject = result.getSubject();
        String sTitle = "Result Updated: " + subject.getSubjectName();
        String sMessage = "Updated results are available for " + subject.getSubjectName() + " (" + exam.getName() + "). Your marks have been updated. Reason: " + reason;
        String sLink = "/student/results?examId=" + exam.getId();

        inTransaction(() -> {
            notificationDAO.save(new Notification(student.getUser(), sTitle, sMessage, "RESULT_UPDATED", "RESULT", result.getId(), sLink));
            return null;
        });

        try {
            if (student.getCurrentSection() != null) {
                List<TeacherSubject> tsList = teacherSubjectDAO.findActiveBySubjectAndSection(subject.getId(), student.getCurrentSection().getId());
                for (TeacherSubject ts : tsList) {
                    User teacherUser = ts.getTeacher().getUser();
                    String tTitle = "Marks Corrected: " + student.getRollNo() + " - " + subject.getSubjectCode();
                    String tMessage = "Marks for " + student.getRollNo() + " in " + subject.getSubjectName()
                            + " were corrected by " + updater.getFullName() + ". Reason: " + reason;
                    String tLink = "/teacher/submitted-results?examId=" + exam.getId();
                    inTransaction(() -> {
                        notificationDAO.save(new Notification(teacherUser, tTitle, tMessage, "RESULT_UPDATED", "RESULT", result.getId(), tLink));
                        return null;
                    });
                }
            }
        } catch (Exception ignored) {
            // Non-critical notification failure
        }
    }

    /** Generates exam creation notifications for relevant teachers and students. */
    public void notifyExamCreated(Exam exam) {
        notifyExamAudience(exam, "EXAM_CREATED", "New Exam Created: " + exam.getName());
    }

    /** Generates or updates exam scheduled notifications for relevant teachers and students. */
    public void notifyExamScheduled(Exam exam) {
        notifyExamAudience(exam, "EXAM_SCHEDULED", "New Exam Scheduled: " + exam.getName());
    }

    /** Generates exam updated notifications when meaningful details change. */
    public void notifyExamUpdated(Exam exam, boolean detailsChanged) {
        if (!detailsChanged) return;
        notifyExamAudience(exam, "EXAM_UPDATED", "Exam Updated: " + exam.getName());
    }

    private void notifyExamAudience(Exam exam, String notificationType, String titlePrefix) {
        if (exam == null || exam.getSemester() == null) return;
        Long semesterId = exam.getSemester().getId();

        String dateStr = exam.getStartTime() != null ? DateUtil.formatForDisplay(exam.getStartTime().toLocalDate()) : "TBA";
        String timeStr = exam.getStartTime() != null ? exam.getStartTime().format(DateTimeFormatter.ofPattern("hh:mm a")) : "TBA";
        String courseStr = exam.getSemester().getCourse() != null ? exam.getSemester().getCourse().getName() : "";
        int semNum = exam.getSemester().getSemesterNumber();

        List<Subject> subjects = subjectDAO.findBySemester(semesterId);
        String subjectNames = subjects.stream().map(Subject::getSubjectName).collect(Collectors.joining(", "));

        String studentMsg = "New exam scheduled: " + exam.getName() + " on " + dateStr + " at " + timeStr + "."
                + " Course: " + courseStr + " (Semester " + semNum + ")"
                + (subjectNames.isEmpty() ? "" : ". Subjects: " + subjectNames) + ".";

        // Notify enrolled students
        List<Student> students = studentDAO.findBySemester(semesterId);
        inTransaction(() -> {
            for (Student student : students) {
                if (student.getUser() == null) continue;
                Long uid = student.getUser().getId();
                if (!notificationDAO.existsByRecipientAndEntity(uid, notificationType, "EXAM", exam.getId())) {
                    notificationDAO.save(new Notification(student.getUser(), titlePrefix, studentMsg,
                            notificationType, "EXAM", exam.getId(), "/student/exams"));
                }
            }
            return null;
        });

        // Notify assigned teachers
        List<TeacherSubject> assignments = teacherSubjectDAO.findActiveBySemester(semesterId);
        Set<Long> notifiedTeacherUids = new HashSet<>();
        inTransaction(() -> {
            for (TeacherSubject ts : assignments) {
                User tUser = ts.getTeacher().getUser();
                if (tUser == null) continue;
                if (notifiedTeacherUids.add(tUser.getId())) {
                    if (!notificationDAO.existsByRecipientAndEntity(tUser.getId(), notificationType, "EXAM", exam.getId())) {
                        String tMsg = "New exam scheduled: " + exam.getName() + " on " + dateStr + " at " + timeStr + "."
                                + " Assigned subject: " + ts.getSubject().getSubjectName()
                                + " (" + ts.getSection().getName() + ", Sem " + semNum + ").";
                        notificationDAO.save(new Notification(tUser, titlePrefix, tMsg,
                                notificationType, "EXAM", exam.getId(), "/teacher/exams"));
                    }
                }
            }
            return null;
        });
    }

    /** Sec. 23's "Re-evaluation status changed" and "Marks updated through authorized process" are the same event here: {@code resolveRequest} only reaches APPROVED after the marks correction (Sec. 14) already happened, so one notification correctly covers both. Fires for the student (this method) AND the admin who assigned it (notifyRevaluationResolvedToAdmin) - Sec. "System automatically notifies both Admin and the student" names both explicitly. */
    public void notifyRevaluationResolved(RevaluationRequest request) {
        User user = request.getStudent().getUser();
        boolean approved = request.getStatus() == RevaluationStatus.APPROVED;
        String title = "Re-evaluation " + (approved ? "Approved" : "Rejected");
        StringBuilder message = new StringBuilder("Your re-evaluation request for "
                + request.getResult().getSubject().getSubjectName() + " ("
                + request.getResult().getExam().getName() + ") has been "
                + (approved ? "approved. Your marks have been updated." : "rejected."));
        if (request.getAdminRemark() != null && !request.getAdminRemark().isBlank()) {
            message.append(" Remark: ").append(request.getAdminRemark());
        }
        if (request.getTeacherReport() != null && !request.getTeacherReport().isBlank()) {
            message.append(" Evaluator's report: ").append(request.getTeacherReport());
        }
        inTransaction(() -> notificationDAO.save(new Notification(user, title, message.toString())));
        emailNotificationService.sendRevaluationDecision(user, request, approved);
    }

    /** The admin half of "notifies both Admin and the student" - the admin who assigned the request, so they see the outcome of a delegation they made without having to keep the whole assigned queue open and re-checking it. */
    public void notifyRevaluationResolvedToAdmin(RevaluationRequest request) {
        if (request.getAssignedBy() == null) {
            return;
        }
        boolean approved = request.getStatus() == RevaluationStatus.APPROVED;
        String title = "Re-evaluation Resolved: " + request.getStudent().getRollNo();
        String message = request.getAssignedTeacher().getUser().getFullName() + " has "
                + (approved ? "approved and updated" : "reviewed and declined") + " the re-evaluation request for "
                + request.getResult().getSubject().getSubjectName() + " (" + request.getResult().getExam().getName() + ").";
        inTransaction(() -> notificationDAO.save(new Notification(request.getAssignedBy(), title, message)));
    }

    /** Fires the moment an admin hands a request to a teacher - the assigned teacher's own "queue" is a live query (TeacherRevaluationServlet), but a notification means they find out immediately rather than only on their next visit to that page. */
    public void notifyRevaluationAssigned(RevaluationRequest request) {
        User teacherUser = request.getAssignedTeacher().getUser();
        String title = "Re-evaluation Assigned to You";
        String message = "You have been assigned to evaluate " + request.getStudent().getRollNo() + "'s re-evaluation "
                + "request for " + request.getResult().getSubject().getSubjectName()
                + " (" + request.getResult().getExam().getName() + ").";
        inTransaction(() -> notificationDAO.save(new Notification(teacherUser, title, message)));
    }

    /** Sec. 23's "Important administrative notices" - fans out to every user whose role is in the notice's audience (Sec. 25's Set&lt;UserRole&gt;), one Notification row per recipient. Called only from the /publish action (Notice.publish()), never on create/edit - a draft notice has no recipients yet by definition. */
    public void notifyNoticePublished(Notice notice) {
        List<User> recipients = new ArrayList<>();
        for (UserRole role : notice.getAudience()) {
            recipients.addAll(userDAO.findByRole(role));
        }
        if (recipients.isEmpty()) {
            return;
        }
        inTransaction(() -> {
            for (User recipient : recipients) {
                notificationDAO.save(new Notification(recipient, "Notice: " + notice.getTitle(), notice.getContent()));
            }
            return null;
        });
        emailNotificationService.sendNoticePublished(recipients, notice);
    }

    public List<Notification> getNotifications(Long userId) {
        return notificationDAO.findByUser(userId);
    }

    /** Called once RegistrationService has actually created the live account - loginIdentifier is the roll_no/employee_code the person will type to sign in, since that (not email) is what Registration makes the login ID. */
    public void notifyRegistrationApproved(User user, String loginIdentifier) {
        inTransaction(() -> notificationDAO.save(new Notification(user, "Registration Approved",
                "Your registration has been approved. You can now log in with ID " + loginIdentifier + ".")));
        emailNotificationService.sendRegistrationApproved(user, loginIdentifier);
    }

    /** No User/account ever exists for a rejected registration (see RegistrationRequest.reject()'s Javadoc), so there is no in-app Notification to write - email (if the person supplied one) is the only channel available here, unlike every other notify* method in this class. */
    public void notifyRegistrationRejected(String email, String fullName, String remark) {
        emailNotificationService.sendRegistrationRejected(email, fullName, remark);
    }

    /** Forgot Password's final step (Sec. "keep Admin in the loop") - the account already exists here (unlike registration), so both channels apply as usual. */
    public void notifyPasswordResetDecision(User user, boolean approved) {
        String title = "Password Reset " + (approved ? "Approved" : "Not Approved");
        String message = approved
                ? "Your password has been changed. You can now log in with your new password."
                : "Your password change request was not approved. Your existing password remains unchanged.";
        inTransaction(() -> notificationDAO.save(new Notification(user, title, message)));
        emailNotificationService.sendPasswordResetDecision(user, approved);
    }

    public long getUnreadCount(Long userId) {
        return notificationDAO.countUnreadByUser(userId);
    }

    /**
     * @throws ResourceNotFoundException if the notification doesn't exist.
     * @throws AuthorizationException    if it belongs to a different user - the same IDOR-proofing {@code MarksheetDownloadServlet} (Phase 12) applies to a resource id, here enforced in the service since two different role-prefixed servlets (admin/student) both call this same method.
     */
    public void markRead(Long notificationId, Long requestingUserId) {
        inTransaction(() -> {
            Notification notification = notificationDAO.findById(notificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
            if (!notification.getUser().getId().equals(requestingUserId)) {
                throw new AuthorizationException("You cannot modify another user's notification.");
            }
            notification.markRead();
            return null;
        });
    }

    public void markAllRead(Long userId) {
        inTransaction(() -> {
            for (Notification notification : notificationDAO.findUnreadByUser(userId)) {
                notification.markRead();
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- transaction helper

    private interface TransactionalWork<T> {
        T run();
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            T result = work.run();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }
}

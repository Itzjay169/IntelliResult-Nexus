package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.service.ActivityLogService;
import com.intelliresult.nexus.service.MarksheetService;
import com.intelliresult.nexus.service.StudentAnalyticsService;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.DateUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Downloads official PDF marksheets with role-based access control.
 * Students can only download their own marksheet (session-scoped).
 * Teachers can download marksheets for students in classes they teach.
 * Admins can download any student's marksheet.
 */
@WebServlet(name = "MarksheetDownloadServlet", urlPatterns = {
        "/student/marksheet/download",
        "/teacher/marksheet/download",
        "/admin/marksheet/download"
})
public class MarksheetDownloadServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(MarksheetDownloadServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final StudentAnalyticsService analyticsService = new StudentAnalyticsService();
    private final ActivityLogService activityLogService = new ActivityLogService();
    private final MarksheetService marksheetService = new MarksheetService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student;

        if (currentUser.getRole() == UserRole.STUDENT) {
            student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
            if (student == null) {
                LOGGER.error("User {} has role STUDENT but no matching Student profile.", currentUser.getEmail());
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } else if (currentUser.getRole() == UserRole.ADMIN) {
            Long studentId = parseLongOrNull(request.getParameter("studentId"));
            if (studentId == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A valid studentId is required.");
                return;
            }
            student = studentDAO.findById(studentId).orElse(null);
            if (student == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Student not found.");
                return;
            }
        } else if (currentUser.getRole() == UserRole.TEACHER) {
            Long studentId = parseLongOrNull(request.getParameter("studentId"));
            if (studentId == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A valid studentId is required.");
                return;
            }
            Teacher teacher = teacherDAO.findByUserId(currentUser.getId()).orElse(null);
            if (teacher == null || !analyticsService.teachesStudent(teacher.getId(), studentId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not authorized to view this student's marksheet.");
                return;
            }
            student = studentDAO.findById(studentId).orElse(null);
            if (student == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Student not found.");
                return;
            }
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Long examId;
        try {
            examId = parseLongOrNull(request.getParameter("examId"));
        } catch (NumberFormatException e) {
            examId = null;
        }
        if (examId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A valid examId is required.");
            return;
        }

        try {
            byte[] pdf = marksheetService.generateMarksheetPdf(
                    student.getId(), examId, currentUser, resolveVerificationBaseUrl(request));

            activityLogService.log(new ActivityLog(currentUser, "MARKSHEET_DOWNLOADED",
                    "Downloaded marksheet for student " + student.getRollNo() + ", exam id " + examId, request.getRemoteAddr()));

            String filename = "marksheet_" + student.getRollNo() + "_"
                    + DateUtil.formatForFilename(LocalDateTime.now()) + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();
        } catch (ResourceNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (BusinessRuleException e) {
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        }
    }

    /**
     * scheme://host[:port]/contextPath, derived from the live request
     * rather than a static config value - Sec. 64 asks for the application
     * URL to be configurable, but a QR code is only useful if it points at
     * whatever host actually served this request (localhost:8080 in dev,
     * the real domain in production), which a fixed property can drift out
     * of sync with. Standard port suppression (no ":80"/" :443") matches
     * how a browser's own address bar renders these same URLs.
     */
    private String resolveVerificationBaseUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme()).append("://").append(request.getServerName());
        boolean isDefaultPort = ("http".equals(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equals(request.getScheme()) && request.getServerPort() == 443);
        if (!isDefaultPort) {
            url.append(':').append(request.getServerPort());
        }
        url.append(request.getContextPath());
        return url.toString();
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}

package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.service.ActivityLogService;
import com.intelliresult.nexus.service.StudentAnalyticsService;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.intelliresult.nexus.util.GradeUtil;

/**
 * {@code /student/profile} - Student profile management.
 * Shows personal and academic profile details and allows updating permitted contact information.
 */
@WebServlet(name = "StudentProfileServlet", urlPatterns = {"/student/profile"})
public class StudentProfileServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentProfileServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
    private final StudentAnalyticsService analyticsService = new StudentAnalyticsService();
    private final ActivityLogService activityLogService = new ActivityLogService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
        if (student == null) {
            LOGGER.error("User {} has role STUDENT but no matching Student profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("student", student);
        List<ResultSummary> summaries = resultSummaryDAO.findAllForStudent(student.getId());
        request.setAttribute("examCount", summaries.size());

        BigDecimal latestSgpa = null;
        BigDecimal cgpa = null;
        if (!summaries.isEmpty()) {
            for (int i = summaries.size() - 1; i >= 0; i--) {
                if (summaries.get(i).getSgpa() != null) {
                    latestSgpa = summaries.get(i).getSgpa();
                    break;
                }
            }
            for (int i = summaries.size() - 1; i >= 0; i--) {
                if (summaries.get(i).getCgpa() != null) {
                    cgpa = summaries.get(i).getCgpa();
                    break;
                }
            }
            if (cgpa == null) {
                List<GradeUtil.WeightedValue> sgpas = new ArrayList<>();
                for (ResultSummary s : summaries) {
                    if (s.getSgpa() != null) {
                        BigDecimal cr = s.getTotalCredits() != null && s.getTotalCredits().compareTo(BigDecimal.ZERO) > 0
                                ? s.getTotalCredits() : BigDecimal.ONE;
                        sgpas.add(new GradeUtil.WeightedValue(s.getSgpa(), cr));
                    }
                }
                cgpa = GradeUtil.weightedAverage(sgpas).orElse(latestSgpa);
            }
        }
        request.setAttribute("currentSgpa", latestSgpa);
        request.setAttribute("currentCgpa", cgpa);

        HttpSession session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute("flashSuccess") != null) {
                request.setAttribute("flashSuccess", session.getAttribute("flashSuccess"));
                session.removeAttribute("flashSuccess");
            }
            if (session.getAttribute("flashError") != null) {
                request.setAttribute("flashError", session.getAttribute("flashError"));
                session.removeAttribute("flashError");
            }
        }

        request.getRequestDispatcher("/student/profile.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
        if (student == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contactNumber = request.getParameter("contactNumber");
        if (contactNumber != null) {
            contactNumber = contactNumber.trim();
        }

        if (contactNumber != null && !contactNumber.isBlank() && !contactNumber.matches("^[+0-9\\s\\-()]{7,20}$")) {
            request.getSession().setAttribute("flashError", "Please enter a valid contact number (7-20 digits or phone format).");
            response.sendRedirect(request.getContextPath() + "/student/profile");
            return;
        }

        final String finalContact = (contactNumber == null || contactNumber.isBlank()) ? null : contactNumber;
        Session hibSession = HibernateUtil.getCurrentSession();
        Transaction tx = hibSession.beginTransaction();
        try {
            student.setContactNumber(finalContact);
            studentDAO.update(student);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.error("Failed to update student profile", e);
            request.getSession().setAttribute("flashError", "Could not update profile. Please try again.");
            response.sendRedirect(request.getContextPath() + "/student/profile");
            return;
        }

        activityLogService.log(new ActivityLog(currentUser, "PROFILE_UPDATED",
                "Updated contact details", request.getRemoteAddr()));

        request.getSession().setAttribute("flashSuccess", "Profile updated successfully.");
        response.sendRedirect(request.getContextPath() + "/student/profile");
    }
}


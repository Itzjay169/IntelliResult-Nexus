package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /student/exams} - Exam schedule view for students.
 * Displays all scheduled, active, and completed examinations for the student's current semester.
 */
@WebServlet(name = "StudentExamScheduleServlet", urlPatterns = {"/student/exams"})
public class StudentExamScheduleServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentExamScheduleServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();

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
        if (student.getCurrentSection() != null && student.getCurrentSection().getSemester() != null) {
            Long semesterId = student.getCurrentSection().getSemester().getId();
            List<Exam> exams = examDAO.findBySemester(semesterId);
            exams.sort(Comparator.comparing(Exam::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
            request.setAttribute("exams", exams);

            List<Subject> subjects = subjectDAO.findBySemester(semesterId);
            request.setAttribute("subjects", subjects);
        } else {
            request.setAttribute("exams", List.of());
            request.setAttribute("subjects", List.of());
        }

        request.getRequestDispatcher("/student/exams.jsp").forward(request, response);
    }
}


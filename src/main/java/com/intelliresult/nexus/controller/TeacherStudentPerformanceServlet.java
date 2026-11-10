package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.service.StudentAnalyticsService;
import com.intelliresult.nexus.service.dto.StudentPerformanceRow;
import com.intelliresult.nexus.service.dto.SubjectInsight;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.GradeUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /teacher/student-performance} - Performance analysis of students taught by the logged-in teacher.
 * Provides authorized student list search, academic filter criteria, and detailed exam-by-exam marks breakdown.
 */
@WebServlet(name = "TeacherStudentPerformanceServlet", urlPatterns = "/teacher/student-performance")
public class TeacherStudentPerformanceServlet extends HttpServlet {

    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
    private final StudentAnalyticsService analyticsService = new StudentAnalyticsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Teacher teacher = currentTeacher(request);
        String studentIdParam = request.getParameter("studentId");
        if (studentIdParam != null && !studentIdParam.isBlank()) {
            try {
                showDetail(request, response, teacher, Long.valueOf(studentIdParam));
                return;
            } catch (NumberFormatException ignored) {}
        }

        showList(request, response, teacher);
    }

    private void showList(HttpServletRequest request, HttpServletResponse response, Teacher teacher)
            throws ServletException, IOException {
        String query = request.getParameter("query");
        Long courseId = parseLongOrNull(request.getParameter("courseId"));
        Long semesterId = parseLongOrNull(request.getParameter("semesterId"));
        Long sectionId = parseLongOrNull(request.getParameter("sectionId"));
        String passFilter = request.getParameter("passFilter");

        request.setAttribute("query", query);
        request.setAttribute("selectedCourseId", courseId);
        request.setAttribute("selectedSemesterId", semesterId);
        request.setAttribute("selectedSectionId", sectionId);
        request.setAttribute("selectedPassFilter", passFilter);

        request.setAttribute("courses", courseDAO.findAll());
        request.setAttribute("semesters", semesterDAO.findAll());
        request.setAttribute("sections", sectionDAO.findAll());

        List<Student> studentsInScope = analyticsService.searchStudentsTaughtBy(teacher.getId(), query);
        List<Student> filtered = studentsInScope.stream().filter(s -> {
            if (courseId != null && (s.getCourse() == null || !s.getCourse().getId().equals(courseId))) {
                return false;
            }
            if (sectionId != null && (s.getCurrentSection() == null || !s.getCurrentSection().getId().equals(sectionId))) {
                return false;
            }
            if (semesterId != null && (s.getCurrentSection() == null
                    || s.getCurrentSection().getSemester() == null
                    || !s.getCurrentSection().getSemester().getId().equals(semesterId))) {
                return false;
            }
            return true;
        }).toList();

        List<StudentPerformanceRow> rows = analyticsService.buildPerformanceOverview(filtered);
        if (passFilter != null && !passFilter.isBlank()) {
            boolean wantPass = "PASS".equalsIgnoreCase(passFilter.trim());
            rows = rows.stream().filter(r -> r.pass() == wantPass).toList();
        }

        request.setAttribute("results", rows);
        request.getRequestDispatcher("/teacher/student-performance.jsp").forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response, Teacher teacher, Long studentId)
            throws ServletException, IOException {
        if (!analyticsService.teachesStudent(teacher.getId(), studentId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Student student = studentDAO.findById(studentId).orElse(null);
        if (student == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("student", student);
        List<ResultSummary> trend = analyticsService.performanceTrend(studentId);
        request.setAttribute("trend", trend);

        BigDecimal latestSgpa = null;
        BigDecimal cumulativeCgpa = null;
        if (!trend.isEmpty()) {
            for (int i = trend.size() - 1; i >= 0; i--) {
                if (trend.get(i).getSgpa() != null) {
                    latestSgpa = trend.get(i).getSgpa();
                    break;
                }
            }
            for (int i = trend.size() - 1; i >= 0; i--) {
                if (trend.get(i).getCgpa() != null) {
                    cumulativeCgpa = trend.get(i).getCgpa();
                    break;
                }
            }
            if (cumulativeCgpa == null) {
                List<GradeUtil.WeightedValue> sgpas = new ArrayList<>();
                for (ResultSummary rs : trend) {
                    if (rs.getSgpa() != null) {
                        BigDecimal cr = rs.getTotalCredits() != null && rs.getTotalCredits().compareTo(BigDecimal.ZERO) > 0
                                ? rs.getTotalCredits() : BigDecimal.ONE;
                        sgpas.add(new GradeUtil.WeightedValue(rs.getSgpa(), cr));
                    }
                }
                cumulativeCgpa = GradeUtil.weightedAverage(sgpas).orElse(latestSgpa);
            }
        }
        request.setAttribute("latestSgpa", latestSgpa);
        request.setAttribute("cumulativeCgpa", cumulativeCgpa);

        Map<Long, ResultSummary> summariesByExamId = new LinkedHashMap<>();
        for (ResultSummary rs : trend) {
            summariesByExamId.put(rs.getExam().getId(), rs);
        }
        request.setAttribute("summariesByExamId", summariesByExamId);

        List<SubjectInsight> insights = analyticsService.subjectStrengthWeakness(studentId);
        request.setAttribute("insights", insights);
        if (!insights.isEmpty()) {
            request.setAttribute("bestSubject", insights.get(0));
            if (insights.size() > 1) {
                request.setAttribute("improvementSubject", insights.get(insights.size() - 1));
            }
        }

        List<Result> calculated = resultDAO.findCalculatedByStudent(studentId);
        Map<Exam, List<Result>> resultsByExam = new LinkedHashMap<>();
        for (Result r : calculated) {
            resultsByExam.computeIfAbsent(r.getExam(), k -> new ArrayList<>()).add(r);
        }
        request.setAttribute("resultsByExam", resultsByExam);
        request.setAttribute("subjectHistory", subjectHistory(studentId));

        request.getRequestDispatcher("/teacher/student-performance-detail.jsp").forward(request, response);
    }

    private Map<String, List<Result>> subjectHistory(Long studentId) {
        Map<String, List<Result>> bySubject = new LinkedHashMap<>();
        for (Result r : resultDAO.findCalculatedByStudent(studentId)) {
            String key = r.getSubject().getSubjectCode() + " - " + r.getSubject().getSubjectName();
            bySubject.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return bySubject;
    }

    private Teacher currentTeacher(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        return teacherDAO.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("No teacher profile for user " + currentUser.getId()));
    }

    private Long parseLongOrNull(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Long.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

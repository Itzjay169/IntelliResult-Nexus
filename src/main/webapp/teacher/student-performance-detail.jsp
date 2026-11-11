<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${student.user.fullName}" scope="request"/>
<c:set var="pageSubtitle" value="${student.rollNo} &mdash; ${student.course.name}" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div style="margin-bottom:var(--space-4);">
    <a href="${ctx}/teacher/student-performance" style="color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">&larr; Back to search</a>
</div>

<!-- Student Profile Information Card -->
<div class="panel" style="margin-bottom:var(--space-6);">
    <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:var(--space-4);">
        <div>
            <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-ink-soft); font-weight:600;">Student Academic Profile</div>
            <h2 style="margin:4px 0; font-family:var(--font-display);"><c:out value="${student.user.fullName}"/></h2>
            <p style="margin:0; color:var(--color-ink-soft); font-size:0.95rem;">
                Roll No: <span style="font-family:var(--font-mono); font-weight:600;"><c:out value="${student.rollNo}"/></span>
                &mdash; Email: <c:out value="${student.user.email}"/>
                <c:if test="${not empty student.contactNumber}">
                    &mdash; Contact: <c:out value="${student.contactNumber}"/>
                </c:if>
            </p>
        </div>
        <div style="font-size:0.9rem; color:var(--color-ink-soft); text-align:right;">
            <div>Course: <strong><c:out value="${student.course.name}"/></strong></div>
            <div>Semester <c:out value="${student.currentSection.semester.semesterNumber}"/> &mdash; Section <c:out value="${student.currentSection.name}"/></div>
            <div>Academic Year: <c:out value="${student.currentSection.semester.academicYear.label}"/></div>
        </div>
    </div>
</div>

<!-- Performance Insights Stat Grid -->
<div class="stat-grid" style="margin-bottom:var(--space-6); grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));">
    <div class="stat-card">
        <div class="label">Exams Completed</div>
        <div class="value"><c:out value="${trend.size()}"/></div>
    </div>
    <c:if test="${not empty trend}">
        <c:set var="latest" value="${trend[trend.size()-1]}"/>
        <div class="stat-card">
            <div class="label">Latest Percentage</div>
            <div class="value"><c:out value="${latest.overallPercentage}"/>%</div>
        </div>
        <div class="stat-card">
            <div class="label">Latest Grade</div>
            <div class="value"><c:out value="${latest.overallGrade}"/></div>
        </div>
        <div class="stat-card">
            <div class="label">Latest SGPA</div>
            <div class="value" style="color:var(--color-brand); font-weight:700;"><c:out value="${latestSgpa != null ? latestSgpa : '-'}"/></div>
        </div>
        <div class="stat-card">
            <div class="label">Cumulative CGPA</div>
            <div class="value" style="color:var(--color-brand); font-weight:700;"><c:out value="${cumulativeCgpa != null ? cumulativeCgpa : '-'}"/></div>
        </div>
        <div class="stat-card">
            <div class="label">Class Rank</div>
            <div class="value ${latest.pass ? 'ok' : 'warn'}"><c:out value="${not empty latest.classRank ? latest.classRank : '-'}"/></div>
        </div>
    </c:if>
</div>

<!-- Subject Strengths and Weaknesses -->
<c:if test="${not empty bestSubject or not empty improvementSubject}">
    <div class="panel-grid" style="margin-bottom:var(--space-6);">
        <c:if test="${not empty bestSubject}">
            <div class="panel">
                <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-verified); font-weight:600;">Strongest Subject</div>
                <h3 style="margin:4px 0; font-family:var(--font-display);"><c:out value="${bestSubject.subjectName}"/></h3>
                <p style="color:var(--color-ink-soft); margin:0;">Average <c:out value="${bestSubject.averagePercentage}"/>% across <c:out value="${bestSubject.examCount}"/> exam<c:if test="${bestSubject.examCount != 1}">s</c:if></p>
            </div>
        </c:if>
        <c:if test="${not empty improvementSubject}">
            <div class="panel">
                <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-danger); font-weight:600;">Area for Improvement</div>
                <h3 style="margin:4px 0; font-family:var(--font-display);"><c:out value="${improvementSubject.subjectName}"/></h3>
                <p style="color:var(--color-ink-soft); margin:0;">Average <c:out value="${improvementSubject.averagePercentage}"/>% across <c:out value="${improvementSubject.examCount}"/> exam<c:if test="${improvementSubject.examCount != 1}">s</c:if></p>
            </div>
        </c:if>
    </div>
</c:if>

<c:choose>
<c:when test="${empty resultsByExam}">
    <div class="panel">
        <div class="panel-empty">
            <p>No published exam results for this student yet.</p>
        </div>
    </div>
</c:when>
<c:otherwise>

    <!-- Trend Chart -->
    <c:if test="${not empty trend}">
        <div class="panel" style="margin-bottom:var(--space-6);">
            <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Performance Trend</h2>
            <canvas id="trendChart" height="90"></canvas>
        </div>
    </c:if>

    <!-- Examination Results with Subject-Wise Breakdown -->
    <c:forEach items="${resultsByExam}" var="entry">
        <c:set var="exam" value="${entry.key}"/>
        <c:set var="examSummary" value="${summariesByExamId[exam.id]}"/>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:var(--space-3); margin-bottom:var(--space-4);">
                <div>
                    <h2 style="margin:0 0 4px; font-family:var(--font-display);"><c:out value="${exam.name}"/></h2>
                    <p style="margin:0; color:var(--color-ink-soft); font-size:0.9rem;">
                        <c:out value="${exam.examType}"/> &mdash; Semester <c:out value="${exam.semester.semesterNumber}"/>
                        <c:if test="${not empty examSummary}">
                            &mdash; <strong>Overall <c:out value="${examSummary.overallPercentage}"/>%</strong>
                            &mdash; Grade <c:out value="${examSummary.overallGrade}"/>
                            <c:if test="${not empty examSummary.sgpa}"> &mdash; <strong>SGPA:</strong> <c:out value="${examSummary.sgpa}"/></c:if>
                            &mdash; <strong>CGPA:</strong> <c:out value="${examSummary.cgpa != null ? examSummary.cgpa : (cumulativeCgpa != null ? cumulativeCgpa : examSummary.sgpa)}"/>
                            <c:if test="${not empty examSummary.classRank}"> &mdash; Rank <c:out value="${examSummary.classRank}"/></c:if>
                            &mdash; <span style="${examSummary.pass ? 'color:var(--color-verified);' : 'color:var(--color-danger);'} font-weight:600;"><c:out value="${examSummary.pass ? 'PASS' : 'FAIL'}"/></span>
                        </c:if>
                    </p>
                </div>
                <div style="display:flex; align-items:center; gap:var(--space-3);">
                    <c:choose>
                        <c:when test="${exam.status == 'PUBLISHED'}"><span class="badge badge-published">PUBLISHED</span></c:when>
                        <c:when test="${exam.status == 'LOCKED'}"><span class="badge badge-locked">LOCKED</span></c:when>
                        <c:otherwise><span class="badge badge-draft">${exam.status}</span></c:otherwise>
                    </c:choose>

                    <c:if test="${not empty examSummary and examSummary.complete}">
                        <a href="${ctx}/teacher/marksheet/download?studentId=${student.id}&examId=${exam.id}" class="btn-secondary" style="width:auto; padding:0.45rem 1rem; font-size:0.85rem; text-decoration:none; display:inline-flex; align-items:center; gap:6px;">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                                <polyline points="7 10 12 15 17 10"></polyline>
                                <line x1="12" y1="15" x2="12" y2="3"></line>
                            </svg>
                            Download Marksheet (PDF)
                        </a>
                    </c:if>
                </div>
            </div>

            <div class="table-scroll">
                <table class="display" style="width:100%;">
                    <thead>
                        <tr>
                            <th>Subject</th>
                            <th>Theory</th>
                            <th>Practical</th>
                            <th>Internal</th>
                            <th>Total Marks</th>
                            <th>Max Marks</th>
                            <th>%</th>
                            <th>Grade</th>
                            <th>Grade Point</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${entry.value}" var="r">
                            <tr>
                                <td><strong><c:out value="${r.subject.subjectCode}"/></strong> &mdash; <c:out value="${r.subject.subjectName}"/></td>
                                <td><c:out value="${not empty r.theoryMarks ? r.theoryMarks : '-'}"/></td>
                                <td><c:out value="${not empty r.practicalMarks ? r.practicalMarks : '-'}"/></td>
                                <td><c:out value="${not empty r.internalMarks ? r.internalMarks : '-'}"/></td>
                                <td style="font-weight:600;"><c:out value="${r.totalMarks}"/></td>
                                <td><c:out value="${r.subject.totalMaxMarks}"/></td>
                                <td><c:out value="${r.percentage}"/>%</td>
                                <td><c:out value="${r.grade}"/></td>
                                <td><c:out value="${r.gradePoint}"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${r.pass}"><span class="badge badge-published">PASS</span></c:when>
                                        <c:otherwise><span class="badge badge-danger">FAIL</span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </div>
    </c:forEach>

    <!-- Marks Obtained vs Required by Subject -->
    <c:if test="${not empty subjectHistory}">
        <div class="panel">
            <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display);">Marks Obtained vs. Required Passing Thresholds</h2>
            <c:forEach items="${subjectHistory}" var="entry">
                <h3 style="font-size:0.95rem; margin:var(--space-4) 0 var(--space-2);"><c:out value="${entry.key}"/></h3>
                <div class="table-scroll">
                <table style="width:100%; font-size:0.9rem;">
                    <thead><tr><th style="text-align:left;">Exam</th><th style="text-align:left;">Theory (obt/req)</th><th style="text-align:left;">Practical (obt/req)</th><th style="text-align:left;">Internal (obt/req)</th><th style="text-align:left;">Result</th></tr></thead>
                    <tbody>
                    <c:forEach items="${entry.value}" var="r">
                        <tr>
                            <td><c:out value="${r.exam.name}"/></td>
                            <td><c:choose><c:when test="${not empty r.theoryMarks}"><c:out value="${r.theoryMarks}"/> / <c:out value="${r.subject.theoryPassingMarks}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                            <td><c:choose><c:when test="${not empty r.practicalMarks}"><c:out value="${r.practicalMarks}"/> / <c:out value="${r.subject.practicalPassingMarks}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                            <td><c:choose><c:when test="${not empty r.internalMarks}"><c:out value="${r.internalMarks}"/> / <c:out value="${r.subject.internalPassingMarks}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                            <td><c:out value="${r.grade}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
                </div>
            </c:forEach>
        </div>
    </c:if>

</c:otherwise>
</c:choose>

<c:if test="${not empty trend}">
<script>
    if (document.getElementById('trendChart') && typeof Chart !== 'undefined') {
        new Chart(document.getElementById('trendChart'), {
            type: 'line',
            data: {
                labels: [<c:forEach items="${trend}" var="s" varStatus="st">"<c:out value="${s.exam.name}"/>"<c:if test="${!st.last}">,</c:if></c:forEach>],
                datasets: [{
                    label: 'Percentage',
                    data: [<c:forEach items="${trend}" var="s" varStatus="st">${s.overallPercentage}<c:if test="${!st.last}">,</c:if></c:forEach>],
                    borderColor: '#B8925A',
                    backgroundColor: 'rgba(184,146,90,0.12)',
                    tension: 0.3,
                    fill: true
                }]
            },
            options: { scales: { y: { beginAtZero: true, max: 100 } }, plugins: { legend: { display: false } } }
        });
    }
</script>
</c:if>

<%@ include file="/common/fragments/teacher-foot.jspf" %>

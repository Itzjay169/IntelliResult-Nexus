<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Dashboard" scope="request"/>
<c:set var="pageSubtitle" value="Welcome back, ${student.user.fullName}" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<div class="panel" style="margin-bottom:var(--space-6); background:linear-gradient(135deg, rgba(184,146,90,0.08) 0%, rgba(16,25,43,0.03) 100%);">
    <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:var(--space-4);">
        <div>
            <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-ink-soft); font-weight:600;">Student Portal</div>
            <h2 style="margin:4px 0; font-family:var(--font-display);"><c:out value="${student.user.fullName}"/></h2>
            <p style="margin:0; color:var(--color-ink-soft); font-size:0.95rem;">
                <span style="font-family:var(--font-mono); font-weight:600;"><c:out value="${student.rollNo}"/></span>
                &mdash; <c:out value="${student.course.name}"/> (<c:out value="${student.course.code}"/>)
                &mdash; Semester <c:out value="${student.currentSection.semester.semesterNumber}"/>, Section <c:out value="${student.currentSection.name}"/>
                (<c:out value="${student.currentSection.semester.academicYear.label}"/>)
            </p>
        </div>
        <div style="display:flex; flex-wrap:wrap; gap:var(--space-2);">
            <a href="${ctx}/student/results" class="btn-primary" style="width:auto; padding:0.45rem 1rem; font-size:0.85rem; text-decoration:none;">My Results &rarr;</a>
            <a href="${ctx}/student/exams" class="btn-secondary" style="width:auto; padding:0.45rem 1rem; font-size:0.85rem; text-decoration:none;">Exam Schedule</a>
            <a href="${ctx}/student/profile" class="btn-secondary" style="width:auto; padding:0.45rem 1rem; font-size:0.85rem; text-decoration:none;">My Profile</a>
        </div>
    </div>
</div>

<div class="stat-grid" style="margin-bottom:var(--space-6);">
    <div class="stat-card">
        <div class="label">Current Percentage</div>
        <div class="value"><c:out value="${not empty latestSummary ? latestSummary.overallPercentage : '&mdash;'}"/>%</div>
    </div>
    <div class="stat-card">
        <div class="label">SGPA</div>
        <div class="value"><c:out value="${empty latestSummary.sgpa ? '&mdash;' : latestSummary.sgpa}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Cumulative CGPA</div>
        <div class="value"><c:out value="${empty currentCgpa ? '&mdash;' : currentCgpa}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Class Rank</div>
        <div class="value ${latestSummary.pass ? 'ok' : 'warn'}"><c:out value="${empty latestSummary.classRank ? '&mdash;' : latestSummary.classRank}"/></div>
    </div>
</div>

<div class="panel-grid" style="margin-bottom:var(--space-6);">
    <!-- Upcoming Exams Widget -->
    <div class="panel">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-3);">
            <h2 style="margin:0; font-family:var(--font-display);">Upcoming Examinations</h2>
            <a href="${ctx}/student/exams" style="font-size:0.85rem; color:var(--color-ink-soft); text-decoration:none;">View all &rarr;</a>
        </div>
        <c:choose>
            <c:when test="${empty upcomingExams}">
                <div class="panel-empty">
                    <p>No upcoming exams scheduled at this time.</p>
                </div>
            </c:when>
            <c:otherwise>
                <div style="display:flex; flex-direction:column; gap:var(--space-3);">
                    <c:forEach items="${upcomingExams}" var="ex">
                        <div style="padding:var(--space-3); border:1px solid var(--color-rule); border-radius:var(--radius-sm); display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:var(--space-2);">
                            <div>
                                <div style="font-weight:600; font-size:0.95rem;"><c:out value="${ex.name}"/></div>
                                <div style="color:var(--color-ink-soft); font-size:0.8rem; margin-top:2px;">
                                    Starts: <c:out value="${ex.startTime}"/> &mdash; <c:out value="${ex.examType}"/>
                                </div>
                            </div>
                            <div>
                                <c:choose>
                                    <c:when test="${ex.status == 'ACTIVE'}"><span class="badge badge-active">ACTIVE NOW</span></c:when>
                                    <c:when test="${ex.status == 'SCHEDULED'}"><span class="badge badge-scheduled">SCHEDULED</span></c:when>
                                    <c:otherwise><span class="badge badge-draft">UPCOMING</span></c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </div>

    <!-- Latest Result Widget -->
    <div class="panel">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-3);">
            <h2 style="margin:0; font-family:var(--font-display);">Latest Published Result</h2>
            <a href="${ctx}/student/results" style="font-size:0.85rem; color:var(--color-ink-soft); text-decoration:none;">Results history &rarr;</a>
        </div>
        <c:choose>
            <c:when test="${empty latestSummary}">
                <div class="panel-empty">
                    <p>No published examination results yet. Ranks and grades will appear here once your first result is published.</p>
                </div>
            </c:when>
            <c:otherwise>
                <div style="padding:var(--space-3); border:1px solid var(--color-rule); border-radius:var(--radius-sm);">
                    <div style="font-size:1.05rem; font-weight:600; margin-bottom:4px;"><c:out value="${latestSummary.exam.name}"/></div>
                    <div style="color:var(--color-ink-soft); font-size:0.9rem; margin-bottom:var(--space-3);">
                        Overall <c:out value="${latestSummary.overallPercentage}"/>% &mdash; Grade <c:out value="${latestSummary.overallGrade}"/>
                        &mdash; <span style="${latestSummary.pass ? '' : 'color:var(--color-danger); font-weight:600;'}"><c:out value="${latestSummary.pass ? 'PASS' : 'FAIL'}"/></span>
                        <c:if test="${not empty latestSummary.classRank}"> &mdash; Rank <c:out value="${latestSummary.classRank}"/></c:if>
                    </div>
                    <c:if test="${latestSummary.complete}">
                        <a href="${ctx}/student/marksheet/download?examId=${latestSummary.exam.id}" class="btn-primary" style="width:auto; padding:0.45rem 1rem; font-size:0.85rem; text-decoration:none; display:inline-flex; align-items:center; gap:6px;">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                                <polyline points="7 10 12 15 17 10"></polyline>
                                <line x1="12" y1="15" x2="12" y2="3"></line>
                            </svg>
                            Download Marksheet (PDF)
                        </a>
                    </c:if>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<c:if test="${not empty bestSubject or not empty improvementSubject}">
    <div class="panel-grid" style="margin-bottom:var(--space-6);">
        <c:if test="${not empty bestSubject}">
            <div class="panel">
                <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-verified); font-weight:600;">Strongest Subject</div>
                <h3 style="margin:4px 0; font-family:var(--font-display);"><c:out value="${bestSubject.subjectName}"/></h3>
                <p style="color:var(--color-ink-soft); margin:0;">Average <c:out value="${bestSubject.averagePercentage}"/>% across <c:out value="${bestSubject.examCount}"/> examination<c:if test="${bestSubject.examCount != 1}">s</c:if></p>
            </div>
        </c:if>
        <c:if test="${not empty improvementSubject}">
            <div class="panel">
                <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-danger); font-weight:600;">Area for Improvement</div>
                <h3 style="margin:4px 0; font-family:var(--font-display);"><c:out value="${improvementSubject.subjectName}"/></h3>
                <p style="color:var(--color-ink-soft); margin:0;">Average <c:out value="${improvementSubject.averagePercentage}"/>% across <c:out value="${improvementSubject.examCount}"/> examination<c:if test="${improvementSubject.examCount != 1}">s</c:if></p>
            </div>
        </c:if>
    </div>
</c:if>

<c:if test="${not empty trend}">
    <div class="panel-grid" style="margin-bottom:var(--space-6);">
        <div class="panel">
            <h2>Performance Trend</h2>
            <canvas id="trendChart" height="200"></canvas>
        </div>
        <div class="panel">
            <h2>Grade Distribution &mdash; <c:out value="${latestSummary.exam.name}"/></h2>
            <canvas id="gradeChart" height="200"></canvas>
        </div>
    </div>
</c:if>

<div class="panel-grid">
    <!-- Recent Notifications -->
    <div class="panel">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-3);">
            <h2 style="margin:0; font-family:var(--font-display);">Recent Notifications</h2>
            <a href="${ctx}/student/notifications" style="font-size:0.85rem; color:var(--color-ink-soft); text-decoration:none;">All alerts &rarr;</a>
        </div>
        <c:choose>
            <c:when test="${empty recentNotifications}">
                <div class="panel-empty">
                    <p>No recent notifications.</p>
                </div>
            </c:when>
            <c:otherwise>
                <div style="display:flex; flex-direction:column; gap:var(--space-2);">
                    <c:forEach items="${recentNotifications}" var="n">
                        <div style="padding:var(--space-2) 0; border-bottom:1px solid var(--color-rule);">
                            <div style="font-weight:600; font-size:0.9rem; display:flex; align-items:center; gap:6px;">
                                <c:if test="${!n.read}"><span style="width:6px; height:6px; border-radius:50%; background:var(--color-seal); display:inline-block;"></span></c:if>
                                <c:out value="${n.title}"/>
                            </div>
                            <div style="font-size:0.82rem; color:var(--color-ink-soft); margin-top:2px;"><c:out value="${n.message}"/></div>
                        </div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </div>

    <!-- Active Notices -->
    <div class="panel">
        <h2 style="margin:0 0 var(--space-3); font-family:var(--font-display);">Institutional Notices</h2>
        <c:choose>
            <c:when test="${empty notices}">
                <div class="panel-empty">
                    <p>No active notices published for students.</p>
                </div>
            </c:when>
            <c:otherwise>
                <div style="display:flex; flex-direction:column; gap:var(--space-2);">
                    <c:forEach items="${notices}" var="notice">
                        <div style="padding:var(--space-2) 0; border-bottom:1px solid var(--color-rule);">
                            <div style="font-weight:600; font-size:0.9rem;"><c:out value="${notice.title}"/></div>
                            <div style="font-size:0.82rem; color:var(--color-ink-soft); margin-top:2px;"><c:out value="${notice.content}"/></div>
                        </div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<c:if test="${not empty latestSummary}">
<script>
    const trendLabels = [<c:forEach items="${trend}" var="t" varStatus="loop">'<c:out value="${t.exam.name}"/>'<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const trendData = [<c:forEach items="${trend}" var="t" varStatus="loop">${t.overallPercentage}<c:if test="${!loop.last}">,</c:if></c:forEach>];

    if (document.getElementById('trendChart') && typeof Chart !== 'undefined') {
        new Chart(document.getElementById('trendChart'), {
            type: 'line',
            data: {
                labels: trendLabels,
                datasets: [{
                    label: 'Overall Percentage',
                    data: trendData,
                    borderColor: '#B8925A',
                    backgroundColor: 'rgba(184, 146, 90, 0.12)',
                    tension: 0.25,
                    fill: true,
                    pointBackgroundColor: '#B8925A'
                }]
            },
            options: {
                scales: { y: { min: 0, max: 100, ticks: { callback: v => v + '%' } } },
                plugins: { legend: { display: false } }
            }
        });
    }

    const gradeLabels = [<c:forEach items="${gradeDistribution}" var="entry" varStatus="loop">'<c:out value="${entry.key}"/>'<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const gradeCounts = [<c:forEach items="${gradeDistribution}" var="entry" varStatus="loop">${entry.value}<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const gradePalette = ['#2F6B52', '#B8925A', '#D4AC76', '#5B6472', '#A3384A', '#10192B'];

    if (document.getElementById('gradeChart') && typeof Chart !== 'undefined') {
        new Chart(document.getElementById('gradeChart'), {
            type: 'doughnut',
            data: {
                labels: gradeLabels,
                datasets: [{ data: gradeCounts, backgroundColor: gradePalette }]
            },
            options: { plugins: { legend: { position: 'bottom' } } }
        });
    }
</script>
</c:if>

<%@ include file="/common/fragments/student-foot.jspf" %>

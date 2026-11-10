<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Exam Schedule" scope="request"/>
<c:set var="pageSubtitle" value="Schedule, dates, and timings for your enrolled semester examinations" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<div class="panel" style="margin-bottom:var(--space-6);">
    <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:var(--space-4);">
        <div>
            <div style="font-size:0.8rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--color-ink-soft); font-weight:600;">Current Academic Enrollment</div>
            <h2 style="margin:4px 0 0; font-family:var(--font-display);"><c:out value="${student.course.name}"/></h2>
            <p style="margin:4px 0 0; color:var(--color-ink-soft);">
                Semester <c:out value="${student.currentSection.semester.semesterNumber}"/> &mdash; Section <c:out value="${student.currentSection.name}"/>
                (<c:out value="${student.currentSection.semester.academicYear.label}"/>)
            </p>
        </div>
        <div style="display:flex; gap:var(--space-2);">
            <a href="${ctx}/student/results" class="btn-secondary" style="width:auto; padding:0.5rem 1rem; text-decoration:none;">View My Results &rarr;</a>
        </div>
    </div>
</div>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Examinations</h2>
    <c:choose>
        <c:when test="${empty exams}">
            <div class="panel-empty">
                <p>No examinations have been scheduled for your semester yet.</p>
            </div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="display" style="width:100%;">
                    <thead>
                        <tr>
                            <th>Exam Name</th>
                            <th>Type</th>
                            <th>Start Time</th>
                            <th>End Time</th>
                            <th>Attempt Limit</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${exams}" var="e">
                            <tr>
                                <td style="font-weight:600;"><c:out value="${e.name}"/></td>
                                <td><c:out value="${e.examType}"/></td>
                                <td><c:out value="${e.startTime}"/></td>
                                <td><c:out value="${e.endTime}"/></td>
                                <td><c:out value="${e.attemptLimit}"/> sitting<c:if test="${e.attemptLimit > 1}">s</c:if></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${e.status == 'PUBLISHED'}"><span class="badge badge-published">PUBLISHED</span></c:when>
                                        <c:when test="${e.status == 'ACTIVE'}"><span class="badge badge-active">ACTIVE</span></c:when>
                                        <c:when test="${e.status == 'SCHEDULED'}"><span class="badge badge-scheduled">SCHEDULED</span></c:when>
                                        <c:when test="${e.status == 'CREATED'}"><span class="badge badge-draft">UPCOMING</span></c:when>
                                        <c:otherwise><span class="badge">${e.status}</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:if test="${e.status == 'PUBLISHED'}">
                                        <a href="${ctx}/student/results?examId=${e.id}" class="btn-secondary" style="width:auto; padding:0.35rem 0.85rem; font-size:0.8rem; text-decoration:none;">View Result</a>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<div class="panel">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Semester Curriculum &amp; Subjects</h2>
    <c:choose>
        <c:when test="${empty subjects}">
            <div class="panel-empty">
                <p>No subjects found for this semester curriculum.</p>
            </div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="display" style="width:100%;">
                    <thead>
                        <tr>
                            <th>Code</th>
                            <th>Subject Name</th>
                            <th>Credits</th>
                            <th>Total Max Marks</th>
                            <th>Passing Marks (Theory / Prac / Int)</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${subjects}" var="sub">
                            <tr>
                                <td style="font-family:var(--font-mono); font-weight:600;"><c:out value="${sub.subjectCode}"/></td>
                                <td><c:out value="${sub.subjectName}"/></td>
                                <td><c:out value="${sub.credits}"/></td>
                                <td><c:out value="${sub.totalMaxMarks}"/></td>
                                <td>
                                    <c:out value="${not empty sub.theoryPassingMarks ? sub.theoryPassingMarks : '-'}"/> /
                                    <c:out value="${not empty sub.practicalPassingMarks ? sub.practicalPassingMarks : '-'}"/> /
                                    <c:out value="${not empty sub.internalPassingMarks ? sub.internalPassingMarks : '-'}"/>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/student-foot.jspf" %>


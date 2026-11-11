<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Student Academic Performance" scope="request"/>
<c:set var="pageSubtitle" value="Institution-wide student performance records, examination analytics, and marks profiles" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="margin-bottom:var(--space-6);">
    <form method="get" action="${ctx}/admin/student-performance" style="display:flex; flex-direction:column; gap:var(--space-3);">
        <div style="display:flex; gap:var(--space-3); flex-wrap:wrap; align-items:flex-end;">
            <div class="field" style="margin-bottom:0; flex:2; min-width:240px;">
                <label for="query">Search student name or roll number</label>
                <input type="text" id="query" name="query" placeholder="e.g. R2025001 or Jane Doe" value="<c:out value='${query}'/>">
            </div>

            <div class="field" style="margin-bottom:0; flex:1; min-width:160px;">
                <label for="courseId">Course</label>
                <select id="courseId" name="courseId">
                    <option value="">All Courses</option>
                    <c:forEach items="${courses}" var="c">
                        <option value="${c.id}" ${c.id == selectedCourseId ? 'selected' : ''}><c:out value="${c.code}"/></option>
                    </c:forEach>
                </select>
            </div>

            <div class="field" style="margin-bottom:0; flex:1; min-width:140px;">
                <label for="semesterId">Semester</label>
                <select id="semesterId" name="semesterId">
                    <option value="">All Semesters</option>
                    <c:forEach items="${semesters}" var="sem">
                        <option value="${sem.id}" ${sem.id == selectedSemesterId ? 'selected' : ''}>Sem <c:out value="${sem.semesterNumber}"/> (<c:out value="${sem.course.code}"/>)</option>
                    </c:forEach>
                </select>
            </div>

            <div class="field" style="margin-bottom:0; flex:1; min-width:140px;">
                <label for="sectionId">Section</label>
                <select id="sectionId" name="sectionId">
                    <option value="">All Sections</option>
                    <c:forEach items="${sections}" var="sec">
                        <option value="${sec.id}" ${sec.id == selectedSectionId ? 'selected' : ''}>Sec <c:out value="${sec.name}"/> (Sem <c:out value="${sec.semester.semesterNumber}"/>)</option>
                    </c:forEach>
                </select>
            </div>

            <div class="field" style="margin-bottom:0; flex:1; min-width:140px;">
                <label for="passFilter">Standing</label>
                <select id="passFilter" name="passFilter">
                    <option value="">All Statuses</option>
                    <option value="PASS" ${selectedPassFilter == 'PASS' ? 'selected' : ''}>Pass Only</option>
                    <option value="FAIL" ${selectedPassFilter == 'FAIL' ? 'selected' : ''}>Fail Only</option>
                </select>
            </div>

            <div style="display:flex; gap:var(--space-2);">
                <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.4rem;">Apply Filters</button>
                <a href="${ctx}/admin/student-performance" class="btn-secondary" style="width:auto; padding:0.65rem 1rem; text-decoration:none;">Reset</a>
            </div>
        </div>
    </form>
</div>

<div class="panel">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-4);">
        <h2 style="margin:0; font-family:var(--font-display);">Students (<c:out value="${results.size()}"/>)</h2>
    </div>

    <c:choose>
        <c:when test="${empty results}">
            <div class="panel-empty">
                <p>No students match the selected filter criteria.</p>
            </div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="display" style="width:100%;">
                    <thead>
                        <tr>
                            <th>Roll No.</th>
                            <th>Student Name</th>
                            <th>Course</th>
                            <th>Semester</th>
                            <th>Section</th>
                            <th>Completed Exams</th>
                            <th>Overall Avg %</th>
                            <th>Latest SGPA</th>
                            <th>Current CGPA</th>
                            <th>Standing</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${results}" var="r">
                            <tr>
                                <td style="font-family:var(--font-mono); font-weight:600;"><c:out value="${r.student().rollNo}"/></td>
                                <td><c:out value="${r.student().user.fullName}"/></td>
                                <td><c:out value="${r.student().course.code}"/></td>
                                <td>Sem <c:out value="${not empty r.student().currentSection ? r.student().currentSection.semester.semesterNumber : '-'}"/></td>
                                <td><c:out value="${not empty r.student().currentSection ? r.student().currentSection.name : '-'}"/></td>
                                <td><c:out value="${r.completedExamsCount()}"/> exam<c:if test="${r.completedExamsCount() != 1}">s</c:if></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${not empty r.averagePercentage()}">
                                            <strong><c:out value="${r.averagePercentage()}"/>%</strong>
                                        </c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${not empty r.latestSgpa() ? r.latestSgpa() : '-'}"/></td>
                                <td><c:out value="${not empty r.currentCgpa() ? r.currentCgpa() : '-'}"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${r.completedExamsCount() == 0}">
                                            <span class="badge" style="background:var(--color-paper-soft); color:var(--color-ink-soft);">NO EXAMS</span>
                                        </c:when>
                                        <c:when test="${r.pass()}">
                                            <span class="badge badge-published">PASS</span>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="badge badge-danger">FAIL</span>
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <a href="${ctx}/admin/student-performance?studentId=${r.student().id}" class="btn-secondary" style="width:auto; padding:0.35rem 0.85rem; font-size:0.82rem; text-decoration:none; white-space:nowrap;">
                                        View Profile &rarr;
                                    </a>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>


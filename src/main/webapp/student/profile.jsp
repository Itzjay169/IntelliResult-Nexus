<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <%@ taglib prefix="c" uri="jakarta.tags.core" %>
        <c:set var="pageTitle" value="My Profile" scope="request" />
        <c:set var="pageSubtitle" value="Institutional registration, academic placement, and personal contact details"
            scope="request" />
        <%@ include file="/common/fragments/student-head.jspf" %>

            <c:if test="${not empty flashSuccess}">
                <div class="auth-success" style="margin-bottom:var(--space-4);">
                    <c:out value="${flashSuccess}" />
                </div>
            </c:if>
            <c:if test="${not empty flashError}">
                <div class="auth-error" style="margin-bottom:var(--space-4);">
                    <c:out value="${flashError}" />
                </div>
            </c:if>

            <div class="stat-grid" style="margin-bottom:var(--space-6);">
                <div class="stat-card">
                    <div class="label">Roll Number</div>
                    <div class="value" style="font-family:var(--font-mono); font-size:1.4rem;">
                        <c:out value="${student.rollNo}" />
                    </div>
                </div>
                <div class="stat-card">
                    <div class="label">Enrolled Course</div>
                    <div class="value" style="font-size:1.1rem;">
                        <c:out value="${student.course.code}" />
                    </div>
                </div>
                <div class="stat-card">
                    <div class="label">Current Semester</div>
                    <div class="value">Sem
                        <c:out value="${student.currentSection.semester.semesterNumber}" />
                    </div>
                </div>
                <div class="stat-card">
                    <div class="label">Current SGPA</div>
                    <div class="value" style="color:var(--color-brand); font-weight:700;">
                        <c:out value="${empty currentSgpa ? '&mdash;' : currentSgpa}" />
                    </div>
                </div>
                <div class="stat-card">
                    <div class="label">Cumulative CGPA</div>
                    <div class="value" style="color:var(--color-brand); font-weight:700;">
                        <c:out value="${empty currentCgpa ? '&mdash;' : currentCgpa}" />
                    </div>
                </div>
            </div>

            <div class="panel-grid" style="margin-bottom:var(--space-6);">
                <div class="panel">
                    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Academic Placement</h2>
                    <table style="width:100%; border-collapse:collapse; font-size:0.95rem;">
                        <tbody>
                            <tr style="border-bottom:1px solid var(--color-rule);">
                                <td style="padding:var(--space-3) 0; color:var(--color-ink-soft); width:40%;">Course
                                </td>
                                <td style="padding:var(--space-3) 0; font-weight:600;">
                                    <c:out value="${student.course.name}" /> (
                                    <c:out value="${student.course.code}" />)
                                </td>
                            </tr>
                            <tr style="border-bottom:1px solid var(--color-rule);">
                                <td style="padding:var(--space-3) 0; color:var(--color-ink-soft);">Department</td>
                                <td style="padding:var(--space-3) 0; font-weight:600;">
                                    <c:out value="${student.course.department.name}" />
                                </td>
                            </tr>
                            <tr style="border-bottom:1px solid var(--color-rule);">
                                <td style="padding:var(--space-3) 0; color:var(--color-ink-soft);">Section</td>
                                <td style="padding:var(--space-3) 0; font-weight:600;">Section
                                    <c:out value="${student.currentSection.name}" />
                                </td>
                            </tr>
                            <tr style="border-bottom:1px solid var(--color-rule);">
                                <td style="padding:var(--space-3) 0; color:var(--color-ink-soft);">Academic Year</td>
                                <td style="padding:var(--space-3) 0; font-weight:600;">
                                    <c:out value="${student.currentSection.semester.academicYear.label}" />
                                </td>
                            </tr>
                            <tr style="border-bottom:1px solid var(--color-rule);">
                                <td style="padding:var(--space-3) 0; color:var(--color-ink-soft);">Admission Year</td>
                                <td style="padding:var(--space-3) 0; font-weight:600;">
                                    <c:out value="${student.admissionYear.label}" />
                                </td>
                            </tr>
                            <tr>
                                <td style="padding:var(--space-3) 0; color:var(--color-ink-soft);">Completed
                                    Examinations</td>
                                <td style="padding:var(--space-3) 0; font-weight:600;">
                                    <c:out value="${examCount}" /> exam<c:if test="${examCount != 1}">s</c:if>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <div class="panel">
                    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Personal &amp; Contact
                        Details</h2>
                    <form method="post" action="${ctx}/student/profile">
                        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

                        <div class="field">
                            <label for="fullName">Full Name (Institutional Record)</label>
                            <input type="text" id="fullName" value="<c:out value='${student.user.fullName}'/>" disabled
                                style="background:var(--color-paper-soft);">
                        </div>

                        <div class="field">
                            <label for="email">Institutional Email</label>
                            <input type="text" id="email" value="<c:out value='${student.user.email}'/>" disabled
                                style="background:var(--color-paper-soft);">
                        </div>

                        <div class="field">
                            <label for="dob">Date of Birth</label>
                            <input type="text" id="dob" value="<c:out value='${student.dateOfBirth}'/>" disabled
                                style="background:var(--color-paper-soft);">
                        </div>

                        <div class="field">
                            <label for="contactNumber">Contact / Mobile Number</label>
                            <input type="text" id="contactNumber" name="contactNumber"
                                value="<c:out value='${student.contactNumber}'/>" placeholder="+1 (555) 000-0000"
                                maxlength="20">
                            <div class="field-hint">You may update your phone or contact number at any time.</div>
                        </div>

                        <div
                            style="display:flex; justify-content:flex-end; gap:var(--space-3); margin-top:var(--space-4);">
                            <button type="submit" class="btn-primary" style="width:auto; padding:0.6rem 1.4rem;">Save
                                Contact Details</button>
                        </div>
                    </form>
                </div>
            </div>

            <%@ include file="/common/fragments/student-foot.jspf" %>
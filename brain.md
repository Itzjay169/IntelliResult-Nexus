# IntelliResult Nexus — Project Brain

## 1. Project Summary

- **Project Name:** IntelliResult Nexus
- **Repository Root:** `c:\Users\jayki\Projectall\IntelliResult-Nexus\IntelliResult-Nexus`
- **Primary Package:** `com.intelliresult.nexus`
- **Application Context Path:** `/intelliresult-nexus`
- **Core Purpose:** IntelliResult Nexus is an enterprise academic examination, student result management, and verified credentials platform designed for universities and higher-education colleges. It governs academic organizational structures, manages multi-phase examination lifecycles, streamlines faculty marks submission with draft/approval workflows, automates complex academic grading engines (including letter grades, grade points, SGPA, cumulative CGPA, section class ranks, and cohort ranks), delivers comparative student performance analytics, broadcasts institutional notices and in-app event notifications, manages revaluation petitions, and generates tamper-evident OpenPDF marksheets featuring cryptographic QR-code verification.
- **Primary Stakeholders / Users:**
  1. **Administrators:** Full institutional control over academic setup, master user records, examination lifecycles, result approvals, result corrections with immutable history, and system audit logging.
  2. **Teachers / Faculty:** Access to assigned departments, subjects, student rosters, marks entry workflows, class performance metrics, and notifications.
  3. **Students:** Access to self-service dashboard, examination schedules, detailed subject-wise results, comparative performance analytics (vs. class average and topper), notifications, revaluation filings, and verifiable marksheet downloads.
- **Current Operational Status:** Fully functioning, robustly tested on Java 17 and Apache Tomcat 11.0.x with MySQL 8.0+ backend. All 88 automated unit and integration tests passing green.

---

## 2. Technology Stack

| Technology | Version | Purpose in Project |
|---|---|---|
| **Java** | `17 (LTS)` | Core runtime language. Leverages Java 17 language features including records for DTOs, sealed hierarchies, pattern matching, and strong memory management. |
| **Jakarta EE / Servlet** | `6.1.0 (Jakarta EE 11)` | Modern web foundation using pure `jakarta.*` packages (`jakarta.servlet`, `jakarta.servlet.http`). Note: Tomcat 9 `javax.*` namespace is strictly avoided. |
| **JSP & JSTL** | `JSP 3.1 / JSTL 3.0.1` | Server-rendered view architecture utilizing standard tags (`<c:out>`, `<c:forEach>`, `<c:choose>`) and modular `.jspf` fragment layouts. |
| **Hibernate ORM** | `7.4.5.Final` | Object-Relational Mapping with Jakarta Persistence 3.2. Configured with `ManagedSessionContext` for request-scoped sessions. |
| **MySQL Connector/J** | `26.7.0` | Production JDBC driver connecting to MySQL 8.0+. Supports window functions (`RANK() OVER`), derived tables, and native generated columns. |
| **HikariCP** | `7.1.0` | High-performance, lightweight JDBC connection pool configured with leak detection, connection timeouts, and dynamic pool management. |
| **Apache Tomcat** | `11.0.25` | Jakarta EE 11 application servlet container hosting the deployed WAR. |
| **OpenPDF** | `2.2.5` | Fork of iText for generating clean, vector-rendered official marksheet PDFs with exact table styling and signature lines. |
| **ZXing ("Zebra Crossing")** | `3.5.4` | High-fidelity 2D matrix barcode generator used to encode digital verification URLs into PNG byte arrays embedded within marksheets. |
| **Apache POI** | `5.5.1` | High-performance spreadsheet manipulation (`XSSF`) for bulk student enrollment and teacher marks roster import/export. |
| **Jackson** | `2.18.2` | JSON serialization engine used for server-side encoding of Chart.js analytical datasets and structured DTO conversions. |
| **jBCrypt** | `0.4` | Secure, salted password hashing algorithm (`BCrypt.hashpw`) utilizing a configurable work factor (default: 10). |
| **Log4j2** | `2.26.0` | Enterprise asynchronous logging framework configured via `log4j2.xml` with console, file, and structured audit appenders. |
| **Sentry Java SDK** | `8.28.0` | Optional real-time application error tracking and performance profiling. Gracefully no-ops when DSN is absent. |
| **Angus Mail (Jakarta Mail)** | `2.0.3` | Jakarta Mail provider for dispatching transactional notification emails over SMTP. |
| **JUnit 5 & H2 Database** | `JUnit 5.11.3 / H2 2.3.232` | Automated testing framework with an isolated in-memory MySQL-compatibility dialect for regression tests. |

---

## 3. Architecture

### Architectural Pattern
IntelliResult Nexus strictly follows the standard enterprise layered architecture:
```
[ Browser / Client ]
         │ (HTTP / JSON / Multi-Part)
         ▼
[ Jakarta Servlet Filters Chain ]  (HibernateSessionFilter -> AuthenticationFilter -> AuthorizationFilter -> CsrfFilter)
         │
         ▼
[ Controller Layer / Servlets ]   (com.intelliresult.nexus.controller.*)
         │
         ▼
[ Service Layer / Business Logic ] (com.intelliresult.nexus.service.*) - Owns Transaction Boundaries
         │
         ▼
[ DAO Layer / Data Access ]       (com.intelliresult.nexus.dao.*)
         │
         ▼
[ Hibernate ORM 7 / Session ]      (Bound via ManagedSessionContext)
         │
         ▼
[ MySQL 8.0+ Database ]
```

### Request Flow Step-by-Step
1. **Incoming Request:** Browser dispatches HTTP request to a target route (e.g. `/student/dashboard`).
2. **Filter Execution:**
   - `HibernateSessionFilter`: Opens a single Hibernate `Session` from `HibernateUtil.getSessionFactory()` and binds it to the current executing thread using `ManagedSessionContext.bind(session)`.
   - `AuthenticationFilter`: Inspects HTTP session for `SESSION_ATTR_USER_ID`. If authenticated, loads the latest `User` entity from database, verifies `is_active` status, and places `User` into `request.setAttribute(REQUEST_ATTR_CURRENT_USER)`.
   - `AuthorizationFilter`: Verifies whether current user's role (`ADMIN`, `TEACHER`, `STUDENT`) has permission to access the URL path. If unauthorized, issues a 403 or redirects.
   - `CsrfFilter`: On state-changing verbs (`POST`, `PUT`, `DELETE`), validates that the request carries a valid CSRF token matching the session attribute.
3. **Controller Execution:** Servlet extracts request parameters, performs initial format validation, and calls the appropriate service method.
4. **Service Execution:** Service implements business rules, opens an explicit transaction (`session.beginTransaction()`), coordinates DAO calls, performs domain calculations, writes activity logs, commits the transaction (`tx.commit()`), and handles rollbacks on failure.
5. **Data Scope:** Controller places resulting domain objects or DTOs into request scope (`request.setAttribute(...)`).
6. **View Rendering:** Controller forwards request to JSP (`request.getRequestDispatcher("/path/to/view.jsp").forward(request, response)`).
7. **Session Unbinding:** In `HibernateSessionFilter`'s `finally` block, `ManagedSessionContext.unbind(sessionFactory)` unbinds the session, and `session.close()` releases DB connections back to HikariCP.

### Hibernate Session & Transaction Rules
- **One Session Per Request:** Managed exclusively by `HibernateSessionFilter`. Servlets and JSPs never open or close sessions.
- **Service-Owned Transactions:** All database transactions (`Transaction tx = session.beginTransaction()`) begin and commit/rollback exclusively inside Service methods.
- **No Controller Transactions:** Controllers must never call `beginTransaction()` or `commit()`.
- **No JSP Logic:** JSPs are strictly for rendering. No database queries or business computations are executed in JSPs.

---

## 4. Folder Structure

```text
IntelliResult-Nexus/
├── pom.xml                               # Maven project descriptor
├── README.md                             # Concise user and deployment guide
├── brain.md                              # Complete architectural AI context file
├── import-students.sql                   # Roster batch SQL for demo environments
├── import-subjects-v2.sql                # Curriculum subject setup SQL
├── import-teachers.sql                   # Faculty batch SQL
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/intelliresult/nexus/
│   │   │       ├── config/               # AppConfig, HibernateUtil, AppConstants
│   │   │       ├── controller/           # 54 HTTP Servlets handling all routes
│   │   │       ├── dao/                  # GenericDAO, base classes, 24 DAO interfaces & impls
│   │   │       ├── entity/               # 24 JPA entities mapped to MySQL tables
│   │   │       ├── filter/               # Request filter chain (Session, Auth, Role, CSRF)
│   │   │       ├── listener/             # AppLifecycleListener (Bootstrap & Hikari cleanup)
│   │   │       ├── service/              # 27 service implementations and calculation engines
│   │   │       │   └── dto/              # Immutable Java records representing DTOs
│   │   │       └── util/                 # Utility helpers (PDF, QR, Excel, Grades, Validation)
│   │   ├── resources/
│   │   │   ├── application.properties    # Environment & application config
│   │   │   ├── hibernate.cfg.xml         # Hibernate 7 session factory configuration
│   │   │   ├── log4j2.xml                # Log4j2 async logging config
│   │   │   └── db/
│   │   │       ├── schema.sql            # Master database DDL (24 tables)
│   │   │       ├── seed-data.sql         # Seed administrators, faculty, and reference records
│   │   │       └── migration/            # Database migration scripts
│   │   └── webapp/
│   │       ├── index.jsp                 # Landing page routing to role dashboards or login
│   │       ├── admin/                    # Admin JSPs (users, setup, exams, approvals, reports)
│   │       ├── teacher/                  # Teacher JSPs (classes, marks entry, drafts, analysis)
│   │       ├── student/                  # Student JSPs (dashboard, results, analysis, marksheet)
│   │       ├── common/                   # Shared templates (error-404, error-500, access-denied)
│   │       │   └── fragments/            # Layout fragments (*-head.jspf, *-foot.jspf)
│   │       ├── assets/                   # CSS styles (tokens.css, app-shell.css, auth.css)
│   │       └── WEB-INF/
│   │           └── web.xml               # Servlet filter-mapping ordering and error pages
│   └── test/
│       ├── java/                         # 16 unit and integration test suites
│       └── resources/
│           └── application.properties    # H2 test environment configuration
```

---

## 5. User Roles and Permissions

### Role Hierarchy
1. `Role.ADMIN`:
   - Highest privilege. Full read, create, update, delete, and restore authority across all institutional entities.
   - Authorized URL prefix: `/admin/*`.
2. `Role.TEACHER`:
   - Faculty member access scoped strictly to assigned subjects, departments, and class sections.
   - Authorized URL prefix: `/teacher/*`.
3. `Role.STUDENT`:
   - Student portal access scoped strictly to the student's own enrolled academic records, results, and notifications.
   - Authorized URL prefix: `/student/*`.

### Authorization Enforcement
- `AuthorizationFilter` intercepts every request:
  - If request URI starts with `/admin/` and currentUser.role != `ADMIN` -> Returns HTTP 403 Forbidden.
  - If request URI starts with `/teacher/` and currentUser.role != `TEACHER` -> Returns HTTP 403 Forbidden.
  - If request URI starts with `/student/` and currentUser.role != `STUDENT` -> Returns HTTP 403 Forbidden.
- Public Routes (`security.public.paths` in `application.properties`):
  `/`, `/login`, `/register`, `/forgot-password`, `/assets/*`, `/common/*`, `/verify/result/*`.
- Authenticated Common Routes:
  `/change-password`, `/logout`.

### IDOR (Insecure Direct Object Reference) Prevention
- In `/student/*` servlets: The student entity is **never** loaded from a query parameter `studentId`. It is strictly retrieved via `studentDAO.findByUserId(currentUser.getId())`. Any URL tampering attempt by a student to supply another student's ID has zero effect.
- In `/teacher/*` servlets: Faculty can only query and submit marks for sections and subjects assigned to them in `teacher_subjects`. TeacherAssignmentService checks `teacherSubjectDAO.isAssigned(...)` prior to any read or write operation.
- In Marksheet Downloads: `/student/marksheet/download` verifies student identity; `/teacher/marksheet/download` verifies teacher subject assignment; `/admin/marksheet/download` allows institutional records verification.

### CSRF Protection
- `CsrfFilter` generates a cryptographically secure token stored in session (`_csrf_token`).
- All HTML forms include `<input type="hidden" name="_csrf" value="${csrfToken}"/>`.
- Incoming `POST` requests validate the token parameter or the `X-CSRF-Token` header. Missing or invalid tokens abort the request with HTTP 403.

---

## 6. Database Design

Master schema is defined in `src/main/resources/db/schema.sql` (24 tables, UTF-8 Unicode, InnoDB engine):

| # | Table Name | Purpose | Primary Key | Foreign Keys | Key Constraints & Rules |
|---|---|---|---|---|---|
| 1 | `departments` | Academic divisions (e.g. Engineering, Sciences) | `id` (BIGINT AUTO) | `deleted_by -> users.id` | Unique `code`, soft-deletable (`deleted`). |
| 2 | `courses` | Degree programs (e.g. B.Tech CSE) | `id` (BIGINT AUTO) | `department_id -> departments.id` | Unique `code`, soft-deletable. |
| 3 | `academic_years` | Academic calendars (e.g. 2026-2027) | `id` (BIGINT AUTO) | `deleted_by -> users.id` | Unique `year_label`, boolean `is_current`. |
| 4 | `semesters` | Academic terms (1 to 8) within courses | `id` (BIGINT AUTO) | `course_id -> courses.id` | Unique composite `(course_id, semester_number)`. |
| 5 | `sections` | Cohort divisions (A, B, C) per semester | `id` (BIGINT AUTO) | `semester_id -> semesters.id` | Unique composite `(semester_id, section_name)`. |
| 6 | `subjects` | Course subjects / modules | `id` (BIGINT AUTO) | `semester_id, department_id` | Unique `subject_code`. Generated column `total_max_marks` (theory+practical+internal). |
| 7 | `users` | Master accounts for Admin, Teacher, Student | `id` (BIGINT AUTO) | `deleted_by -> users.id` | Unique `email`, BCrypt `password_hash`, enum `role`. |
| 8 | `registration_requests` | Prospective student enrollment registrations | `id` (BIGINT AUTO) | `reviewed_by -> users.id` | Status: `PENDING`, `APPROVED`, `REJECTED`. |
| 9 | `otp_codes` | One-time passwords for password recovery | `id` (BIGINT AUTO) | `user_id -> users.id` | 6-digit OTP, expiration timestamp, boolean `used`. |
| 10 | `password_reset_requests`| Administrative password reset tokens | `id` (BIGINT AUTO) | `user_id, reviewed_by` | Cryptographic hex token, status workflow. |
| 11 | `students` | Extended profile for student users | `id` (BIGINT AUTO) | `user_id, current_course_id, current_semester_id, current_section_id` | Unique `roll_no`, unique `user_id`. |
| 12 | `teachers` | Extended profile for faculty users | `id` (BIGINT AUTO) | `user_id, department_id` | Unique `employee_code`, unique `user_id`. |
| 13 | `teacher_subjects` | Faculty subject assignments | `id` (BIGINT AUTO) | `teacher_id, subject_id, section_id, assigned_by` | Authorization mapping for teacher marks entry. |
| 14 | `exams` | Examination master records | `id` (BIGINT AUTO) | `academic_year_id, semester_id, created_by` | Lifecycle enum `status`: `DRAFT` to `LOCKED`. |
| 15 | `results` | Student subject-level marks and evaluation | `id` (BIGINT AUTO) | `student_id, exam_id, subject_id, entered_by, approved_by` | Unique `(student_id, exam_id, subject_id)`. Tracks theory, practical, internal, total, grade, GP, pass. |
| 16 | `result_history` | Immutable audit trail for result modifications | `id` (BIGINT AUTO) | `result_id, modified_by` | Captures old/new marks, reason, timestamp. |
| 17 | `revaluation_requests` | Student petitions for marks review | `id` (BIGINT AUTO) | `result_id, student_id, reviewed_by` | Workflow: `PENDING`, `APPROVED`, `REJECTED`. |
| 18 | `grading_rules` | Institutional letter grading bands (A+, A, B...) | `id` (BIGINT AUTO) | `created_by -> users.id` | `min_percentage`, `max_percentage`, `grade_point`. |
| 19 | `notifications` | Database-backed user alert records | `id` (BIGINT AUTO) | `user_id -> users.id` | `title`, `message`, `notification_type`, `read` flag. |
| 20 | `activity_logs` | Security and administrative audit trail | `id` (BIGINT AUTO) | `user_id -> users.id` | Action name, entity type/id, IP address, user agent. |
| 21 | `notices` | Campus and institutional announcements | `id` (BIGINT AUTO) | `published_by -> users.id` | Target audience: `ALL`, `STUDENTS`, `TEACHERS`. |
| 22 | `system_settings` | Key-value institutional metadata and flags | `id` (BIGINT AUTO) | None | Institution name, address, logo URI, registrar name. |
| 23 | `result_summaries` | Precomputed examination-level summaries | `id` (BIGINT AUTO) | `student_id, exam_id` | Unique `(student_id, exam_id)`. Stores overall percentage, SGPA, CGPA, class rank, pass/fail. |
| 24 | `marksheet_verifications`| Cryptographic tokens for marksheet auth | `id` (BIGINT AUTO) | `student_id, exam_id` | Unique cryptographic `verification_token`. |

---

## 7. Entity and DAO Layer

### Entity Hierarchy
- `BaseEntity`: Base class with `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`.
- `CreatedAtEntity`: Extends `BaseEntity` with immutable `@Column(name = "created_at", updatable = false) private LocalDateTime createdAt;`.
- `TimestampedEntity`: Extends `CreatedAtEntity` with automatic `@Column(name = "updated_at") private LocalDateTime updatedAt;`.
- `SoftDeletableEntity`: Extends `TimestampedEntity` with soft-deletion fields: `boolean deleted`, `LocalDateTime deletedAt`, `User deletedBy`.

### Collection Design Principle
- **Bidirectional `@OneToMany` collections are intentionally avoided across all entities.**
- Entities exclusively declare unidirectional `@ManyToOne(fetch = FetchType.LAZY)` references to their parents.
- Child entities are never loaded through cascading collection getters (which trigger N+1 query storms or lazy initialization exceptions outside transactions).
- Instead, child entities are always queried via explicit, indexed DAO methods (e.g. `subjectDAO.findBySemester(semesterId)`).

### DAO Hierarchy
- `GenericDAO<T, ID>`: Standard contract (`findById`, `findAll`, `save`, `update`, `delete`, `count`).
- `AbstractDAO<T>`: Implements `GenericDAO` using `session()`, which resolves the current thread-bound session via `HibernateUtil.getSessionFactory().getCurrentSession()`.
- `SoftDeletableDAO<T>`: Extends `GenericDAO` with `findAllActive()`, `softDelete(Long id, Long deletedBy)`, `restore(Long id)`.
- `AbstractSoftDeletableDAO<T>`: Implements soft deletion queries filtering by `e.deleted = false`.

---

## 8. Services and Business Logic

The service layer encapsulates 100% of the business logic and database transaction demarcations:

1. **`UserService`:** User lifecycle, BCrypt credential verification, status toggling, role assignment.
2. **`AcademicSetupService`:** Hierarchy management (Departments, Courses, Academic Years, Semesters, Sections).
3. **`SubjectService`:** Curriculum subjects, component structures (theory/practical/internal max and passing thresholds).
4. **`TeacherAssignmentService`:** Faculty allocations to subjects and sections; assignment authorization validation.
5. **`ExamService`:** Exam creation, date and schedule validation, and lifecycle phase advancement.
6. **`GradingRuleService`:** Letter grade scales, percentage bounds, grade points, and passing criteria.
7. **`NoticeService`:** Campus-wide and role-targeted broadcast notice management.
8. **`SystemSettingsService`:** Institutional branding, registrar name, college title, and contact details.
9. **`MarksEntryService`:** Faculty marks entry; handles batch draft saves, strict threshold validation, and formal submissions.
10. **`ResultService`:** Result status transitions (`SUBMITTED`, `APPROVED`, `PUBLISHED`, `LOCKED`), administrative result corrections.
11. **`ResultCalculationService`:** Core calculation engine:
    - Subject calculations: `total = theory + practical + internal`, percentage, letter grade, grade point, pass/fail status.
    - Exam summary: Credit-weighted SGPA (or unweighted grade point mean if uncredited), cumulative CGPA across exams, section class rank (`RANK() OVER`), and overall rank.
12. **`ResultRankingService`:** Cohort ranking coordinator executing MySQL window queries and updating `result_summaries`.
13. **`StudentAnalyticsService`:** Computes performance metrics: personal percentage, section class average, section topper, subject comparison bars, strength areas, and improvement areas.
14. **`RevaluationService`:** Student revaluation filing, administrative review, approved mark amendments, and automatic recalculations.
15. **`NotificationService`:** Database-backed alert dispatching for exam scheduling and result publication events.
16. **`EmailNotificationService`:** Asynchronous transactional email sender utilizing Jakarta Mail / Angus Mail.
17. **`MarksheetService`:** Bundles student records, subject rows, SGPA, CGPA, institutional settings, and QR verification tokens into `MarksheetData`.
18. **`VerificationService`:** Resolves public marksheet verification tokens and validates result integrity.
19. **`ReportService`:** Compiles administrative and teacher academic performance reports and tabulations.
20. **`StudentImportService`:** Processes bulk student rosters from Excel files (.xlsx).
21. **`MarksImportService`:** Processes bulk marks rosters from Excel spreadsheets with row-by-row validation.
22. **`PasswordResetService`:** Self-service OTP generation, email dispatching, and credential resetting.
23. **`RegistrationService`:** Prospective student self-registration and administrative approval pipeline.
24. **`ActivityLogService`:** Writes security and administrative audit events to `activity_logs`.
25. **`DashboardService`:** Aggregates administrative system-wide operational metrics and pending action counts.
26. **`TeacherDashboardService`:** Aggregates teacher workload metrics: assigned classes, pending marks entries, and notifications.
27. **`AuthenticationService`:** Handles login credentials verification, role resolution, and security session initialization.

---

## 9. Controllers and Routes

All routes are mapped to dedicated Jakarta HTTP Servlets:

| Route Path | Servlet Class | Role Required | HTTP Methods | Core Purpose |
|---|---|---|---|---|
| `/login` | `LoginServlet` | Public | `GET`, `POST` | User authentication and session establishment |
| `/logout` | `LogoutServlet` | Authenticated | `POST` | Invalidation of user session and cleanup |
| `/register` | `RegistrationServlet` | Public | `GET`, `POST` | Prospective student enrollment application |
| `/forgot-password` | `ForgotPasswordServlet` | Public | `GET`, `POST` | Self-service password recovery via OTP |
| `/change-password` | `PasswordChangeServlet` | Authenticated | `GET`, `POST` | Password change for signed-in users |
| `/verify/result/*` | `VerificationServlet` | Public | `GET` | Public QR marksheet verification portal |
| `/admin/dashboard` | `AdminDashboardServlet` | `ADMIN` | `GET` | Institutional KPI summary and quick actions |
| `/admin/academic-setup` | `AcademicSetupHubServlet` | `ADMIN` | `GET` | Hub for courses, semesters, and departments |
| `/admin/departments` | `DepartmentServlet` | `ADMIN` | `GET`, `POST` | Department management and soft deletion |
| `/admin/courses` | `CourseServlet` | `ADMIN` | `GET`, `POST` | Degree program management |
| `/admin/academic-years` | `AcademicYearServlet` | `ADMIN` | `GET`, `POST` | Academic session years and active toggle |
| `/admin/semesters` | `SemesterServlet` | `ADMIN` | `GET`, `POST` | Course semester structure management |
| `/admin/sections` | `SectionServlet` | `ADMIN` | `GET`, `POST` | Class section allocation |
| `/admin/subjects` | `SubjectServlet` | `ADMIN` | `GET`, `POST` | Curriculum subjects and max marks setup |
| `/admin/teachers/assign` | `TeacherAssignmentServlet` | `ADMIN` | `GET`, `POST` | Faculty subject assignments |
| `/admin/users` | `UserListServlet` | `ADMIN` | `GET` | Master account registry |
| `/admin/users/new` | `UserFormServlet` | `ADMIN` | `GET`, `POST` | Create new admin, teacher, or student account |
| `/admin/users/edit` | `UserFormServlet` | `ADMIN` | `GET`, `POST` | Edit existing account details |
| `/admin/users/import` | `StudentImportServlet` | `ADMIN` | `GET`, `POST` | Batch student import from Excel |
| `/admin/users/export` | `StudentImportServlet` | `ADMIN` | `GET` | Export student registry to Excel |
| `/admin/students` | `StudentListServlet` | `ADMIN` | `GET` | Filterable student roster |
| `/admin/teachers` | `TeacherListServlet` | `ADMIN` | `GET` | Filterable faculty roster |
| `/admin/users/status` | `UserStatusServlet` | `ADMIN` | `POST` | Activate/Deactivate user accounts |
| `/admin/users/reset-password` | `UserPasswordResetServlet` | `ADMIN` | `POST` | Administrative password reset |
| `/admin/exams` | `ExamServlet` | `ADMIN` | `GET`, `POST` | Exam creation and status advancement |
| `/admin/grading-rules` | `GradingRuleServlet` | `ADMIN` | `GET`, `POST` | Institutional grade points and percentage rules |
| `/admin/results/approvals`| `ResultApprovalServlet` | `ADMIN` | `GET`, `POST` | Review, approve, publish, and lock marks |
| `/admin/results` | `ResultServlet` | `ADMIN` | `GET`, `POST` | Master result matrix and result correction |
| `/admin/student-performance` | `AdminStudentPerformanceServlet` | `ADMIN` | `GET` | Institutional student performance directory |
| `/admin/revaluations` | `AdminRevaluationServlet` | `ADMIN` | `GET`, `POST` | Revaluation petition review and approval |
| `/admin/notices` | `NoticeServlet` | `ADMIN` | `GET`, `POST` | Institutional announcements broadcast |
| `/admin/notifications` | `NotificationServlet` | `ADMIN` | `GET`, `POST` | Administrator alerts and read status |
| `/admin/activity-logs` | `AdminActivityLogServlet` | `ADMIN` | `GET` | Security and audit event viewer |
| `/admin/settings` | `SystemSettingsServlet` | `ADMIN` | `GET`, `POST` | College name, logo URI, registrar settings |
| `/admin/recycle-bin` | `RecycleBinServlet` | `ADMIN` | `GET`, `POST` | Soft-deleted entity viewer and restore tool |
| `/admin/reports` | `ReportServlet` | `ADMIN` | `GET`, `POST` | Tabular institutional result reports |
| `/admin/marksheet/download`| `MarksheetDownloadServlet` | `ADMIN` | `GET` | Official PDF marksheet download (admin view) |
| `/teacher/dashboard` | `TeacherDashboardServlet` | `TEACHER` | `GET` | Teacher workload, classes, and pending marks |
| `/teacher/my-classes` | `TeacherClassesServlet` | `TEACHER` | `GET` | Assigned class sections |
| `/teacher/my-subjects` | `TeacherClassesServlet` | `TEACHER` | `GET` | Assigned curriculum subjects |
| `/teacher/exams` | `TeacherExamsServlet` | `TEACHER` | `GET` | Assigned exam schedule |
| `/teacher/marks-entry` | `MarksEntryServlet` | `TEACHER` | `GET`, `POST` | Marks entry grid: theory, practical, internal |
| `/teacher/marks-entry/import`| `MarksImportServlet` | `TEACHER` | `POST` | Excel marks roster batch upload |
| `/teacher/marks-entry/export`| `MarksImportServlet` | `TEACHER` | `GET` | Blank or pre-filled Excel marks roster template |
| `/teacher/draft-results` | `TeacherResultsServlet` | `TEACHER` | `GET` | Saved draft marks rosters |
| `/teacher/submitted-results`| `TeacherResultsServlet` | `TEACHER` | `GET` | Submitted marks awaiting admin review |
| `/teacher/student-performance`| `TeacherStudentPerformanceServlet`| `TEACHER` | `GET` | Assigned students performance overview |
| `/teacher/activity` | `TeacherActivityServlet` | `TEACHER` | `GET` | Personal marks submission activity log |
| `/teacher/notifications` | `NotificationServlet` | `TEACHER` | `GET`, `POST` | Faculty notifications and read status |
| `/teacher/reports` | `TeacherReportServlet` | `TEACHER` | `GET` | Section performance reports |
| `/teacher/marksheet/download`| `MarksheetDownloadServlet` | `TEACHER` | `GET` | Authorized student marksheet PDF download |
| `/student/dashboard` | `StudentDashboardServlet` | `STUDENT` | `GET` | Student portal, latest results, SGPA, CGPA |
| `/student/results` | `StudentResultServlet` | `STUDENT` | `GET` | Published examination results & breakdowns |
| `/student/exams` | `StudentExamScheduleServlet` | `STUDENT` | `GET` | Student scheduled examinations calendar |
| `/student/analysis` | `StudentAnalysisServlet` | `STUDENT` | `GET` | Performance analytics (topper & class average) |
| `/student/marksheet` | `MarksheetServlet` | `STUDENT` | `GET` | Download center for published marksheets |
| `/student/marksheet/download`| `MarksheetDownloadServlet` | `STUDENT` | `GET` | PDF marksheet download for authenticated student|
| `/student/revaluation` | `StudentRevaluationServlet` | `STUDENT` | `GET`, `POST` | File revaluation petition for published result |
| `/student/profile` | `StudentProfileServlet` | `STUDENT` | `GET`, `POST` | View academic placement; update contact number |
| `/student/notifications` | `NotificationServlet` | `STUDENT` | `GET`, `POST` | Student notifications and read status |

---

## 10. Security

1. **Authentication Flow:**
   - Password comparison via `BCrypt.checkpw(plainPassword, user.getPasswordHash())`.
   - `request.changeSessionId()` is executed immediately upon successful sign-in to prevent session fixation attacks.
   - User ID stored in `session.setAttribute(SESSION_ATTR_USER_ID, user.getId())`.
2. **Session Security:**
   - 30-minute idle session expiration (`app.session.timeoutMinutes=30`).
   - `HttpOnly` cookie enforcement prevents JavaScript cross-site access to the session identifier.
3. **Role-Based Access Control (RBAC):**
   - Strictly enforced by `AuthorizationFilter` before any servlet invocation.
   - Tampered requests or unauthorized role paths receive HTTP 403 Forbidden.
4. **CSRF Defense:**
   - Handled via `CsrfFilter` on all state-changing operations (`POST`).
   - Token is injected into JSP forms via `<input type="hidden" name="_csrf" value="${csrfToken}"/>`.
5. **Open Redirect Protection:**
   - `LoginServlet` validates redirect target URLs (`returnUrl`): Only local relative paths starting with `/` (and not starting with `//`) are accepted. External redirects are rejected.
6. **Input Sanitization:**
   - Output rendered through `<c:out value="..."/>` in JSPs prevents XSS attacks.
   - Parameter parsing utilities (`ValidationUtil`) enforce numeric limits, non-empty text, and valid email patterns.

---

## 11. Core Workflows

### 1. Exam Lifecycle & Notification
1. Admin creates an exam (`ExamServlet`), associating it with an Academic Year, Semester, and Course. Status is initially `DRAFT`.
2. Admin sets exam date/time and advances status to `SCHEDULED`.
3. `NotificationService` automatically generates in-app notifications for:
   - All teachers assigned to subjects in that semester/section.
   - All students enrolled in that semester/section.
4. Duplicate notifications are prevented by checking prior event records.

### 2. Marks Entry & Submission
1. Teacher selects assigned subject and section in `/teacher/marks-entry`.
2. Teacher enters component scores (theory, practical, internal). Client-side JavaScript provides live total feedback for convenience only.
3. Teacher clicks "Save Draft": Marks are persisted with status `DRAFT`.
4. Teacher clicks "Submit Marks": System verifies that all assigned students have marks within valid maximum bounds, then updates status to `SUBMITTED`.
5. Submitted rosters can no longer be edited by the teacher.

### 3. Result Approval, Calculation & Publication
1. Admin reviews submitted rosters in `/admin/results/approvals`.
2. Admin clicks "Approve & Publish":
   - Result records transition to `PUBLISHED`.
   - `ResultCalculationService` executes:
     - Calculates subject percentage, letter grade, and grade points via `GradingRuleDAO`.
     - Calculates exam total marks, credit-weighted SGPA (or unweighted grade point mean if uncredited), and cumulative CGPA.
     - Invokes MySQL window query `RANK() OVER (ORDER BY total_marks DESC)` to compute section class rank and cohort rank.
     - Inserts or updates `result_summaries`.
   - `NotificationService` dispatches result publication notifications to participating students and teachers.

### 4. Result Correction & Audit History
1. If an error is identified in a published result, an Admin opens `/admin/results` and submits a correction with an audit reason.
2. `ResultService` records the old and new marks in `result_history`.
3. The subject result is recomputed, the student's `result_summaries` row is refreshed, and section ranks are recalculated.

### 5. Revaluation Workflow
1. Student views eligible published results in `/student/revaluation` and submits a revaluation request with a justification.
2. System inserts a record into `revaluation_requests` with status `PENDING`.
3. Admin reviews pending requests in `/admin/revaluations`.
4. If approved and marks change, the result correction engine executes, updating marks, `result_history`, and summary ranks. The student receives an outcome notification.

### 6. Marksheet Generation & Verification
1. Student accesses `/student/marksheet` and downloads their PDF.
2. `MarksheetService` generates or reuses a cryptographic token stored in `marksheet_verifications`.
3. `QRCodeUtil` creates a QR code encoding the verification URL (`https://.../verify/result/{token}`).
4. `PDFUtil` renders a vector PDF marksheet with student details, component breakdown, SGPA, CGPA, institutional header, authorized signatory lines, and QR verification image.
5. Anyone scanning the QR code is routed to `VerificationServlet`, which displays authentic examination credentials directly from the database.

---

## 12. UI and Frontend

- **Styling Architecture:**
  - `assets/css/tokens.css`: Core design system variables (`--color-primary`, `--color-ink`, `--color-rule`, `--font-sans`, spacing scales).
  - `assets/css/app-shell.css`: Responsive administrative dashboard shell, sidebar drawers, cards, panels, tables, and badge styles.
  - `assets/css/auth.css`: Centered sign-in, registration, and password recovery cards.
- **Componentized Fragments:**
  - Admin: `common/fragments/admin-head.jspf` and `admin-foot.jspf`.
  - Teacher: `common/fragments/teacher-head.jspf` and `teacher-foot.jspf`.
  - Student: `common/fragments/student-head.jspf` and `student-foot.jspf`.
- **Client Scripting & Visualizations:**
  - Vanilla JavaScript for sidebar toggles, marks grid row total calculations, and CSRF token propagation.
  - Chart.js integration for student comparative performance charts (You vs. Class Average, You vs. Topper, Subject breakdown).

---

## 13. Configuration

### Configuration Precedence
`AppConfig.java` resolves settings using the following priority order:
1. System Environment Variables (e.g. `INTELLIRESULT_DB_PASSWORD`)
2. `src/main/resources/application.properties`
3. Static fallbacks in code

### Mapping Convention
Keys in `application.properties` map to environment variables by:
- Prefixing with `INTELLIRESULT_`
- Replacing dots `.` with underscores `_`
- Uppercasing all characters
*(Example: `db.password` -> `INTELLIRESULT_DB_PASSWORD`)*

### Essential Configuration Keys
- `db.url`: JDBC URL (default: `jdbc:mysql://localhost:3306/intelliresult_nexus?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8`)
- `db.username`: Database username (default: `intelliresult_app` or `root`)
- `db.password`: Must be provided via `INTELLIRESULT_DB_PASSWORD`.
- `db.schema.management`: `validate` in production; `update` in rapid local development.
- `app.session.timeoutMinutes`: Session expiration in minutes (default: `30`).
- `security.bcrypt.cost`: BCrypt work factor (default: `10`).
- `import.excel.maxFileSizeBytes`: Maximum file size for Excel imports (default: `5242880` / 5MB).
- `mail.host`, `mail.port`, `mail.username`, `mail.password`: SMTP email settings.
- `sentry.dsn`: Sentry DSN for remote error monitoring (optional).

---

## 14. Build, Run, and Deploy

### First-Time Run (Initial Setup)
1. **Requirements:** Java 17+, Maven 3.8+, MySQL 8.0+, Apache Tomcat 11.0.x.
2. **Database Initialization:**
   ```bash
   mysql -u root -p
   CREATE DATABASE intelliresult_nexus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   exit;
   mysql -u root -p intelliresult_nexus < src/main/resources/db/schema.sql
   mysql -u root -p intelliresult_nexus < src/main/resources/db/seed-data.sql
   ```
3. **Environment Setup:**
   ```powershell
   $env:INTELLIRESULT_DB_USERNAME="root"
   $env:INTELLIRESULT_DB_PASSWORD="YOUR_DATABASE_PASSWORD"
   ```
4. **Compile & Package:**
   ```bash
   mvn clean package
   ```
5. **Tomcat Deployment:**
   Copy `target/intelliresult-nexus.war` to Tomcat's `webapps/` folder:
   ```powershell
   Copy-Item "target\intelliresult-nexus.war" "$env:CATALINA_HOME\webapps\" -Force
   Set-Location "$env:CATALINA_HOME\bin"
   .\catalina.bat run
   ```
6. **Access:** Navigate to `http://localhost:8080/intelliresult-nexus/login`.

### Subsequent Runs (Starting Again)
- **Without Code Changes:** Ensure MySQL is running, export credentials, and start Tomcat:
  ```powershell
  $env:INTELLIRESULT_DB_USERNAME="root"
  $env:INTELLIRESULT_DB_PASSWORD="YOUR_DATABASE_PASSWORD"
  Set-Location "C:\path\to\apache-tomcat-11.0.x\bin"
  .\catalina.bat run
  ```
- **After Code Modifications:** Re-package and copy the updated WAR:
  ```powershell
  mvn package -DskipTests
  Copy-Item "target\intelliresult-nexus.war" "C:\path\to\apache-tomcat-11.0.x\webapps\" -Force
  ```

---

## 15. Testing

- **Automated Test Suite:**
  - Located in `src/test/java/com/intelliresult/nexus/`.
  - Configured with JUnit 5 and an in-memory H2 database (`jdbc:h2:mem:intelliresult_test;MODE=MySQL`).
  - Total tests: **88 test methods across 16 test classes**. All 88 pass with 0 failures and 0 errors.
- **Execution Command:**
  ```bash
  mvn test
  ```
- **Key Test Suites:**
  - `AuthenticationServiceIntegrationTest`: Credential validation, active state checks, password hashing.
  - `NotificationAndPerformanceIntegrationTest`: Notifications generation, student performance aggregation.
  - `ResultServiceIntegrationTest`: Marks approval, publication, locking, and status transitions.
  - `RevaluationServiceIntegrationTest`: Petition workflow and mark modification recalculation.
  - `StudentImportServiceIntegrationTest`: Bulk Excel parsing and validation.
  - `PDFUtilTest`: OpenPDF marksheet generation and byte structure validation.
  - `QRCodeUtilTest`: ZXing QR code image generation.
  - `GradeUtilTest`: Grading rule calculations.
  - `ValidationUtilTest`: Security and format sanitization unit tests.

---

## 16. Current Status

| Module | Status | Notes |
|---|---|---|
| **Authentication & RBAC** | `Implemented` | Multi-role login, BCrypt hashing, session fixation defense, CSRF protection. |
| **Admin Dashboard** | `Implemented` | KPI metrics, quick links, system overview. |
| **User Management** | `Implemented` | Admin, teacher, student CRUD, status toggling, password resets. |
| **Academic Setup** | `Implemented` | Departments, courses, academic years, semesters, sections, subjects. |
| **Teacher Assignment** | `Implemented` | Faculty allocations to subjects and sections with authorization checks. |
| **Exam Management** | `Implemented` | Exam creation, scheduling, and multi-phase status lifecycle transitions. |
| **Marks Entry** | `Implemented` | Theory, practical, internal marks entry, draft saving, and final submission. |
| **Result Calculation Engine** | `Implemented` | Automated subject grading, grade points, SGPA, CGPA, and MySQL window ranking. |
| **Approval & Publication** | `Implemented` | Admin review, bulk-publish, bulk-lock, and result correction with audit history. |
| **Student Dashboard & Profile** | `Implemented` | Percentage, SGPA, CGPA, class rank, notices, editable contact details. |
| **Comparative Analytics** | `Implemented` | Student vs. class average, topper comparison, strengths, and improvement areas. |
| **Notifications** | `Implemented` | In-app alerts for scheduled exams and published results with unread badges. |
| **Revaluation System** | `Implemented` | Student petition submission, admin review, and automatic recalculations. |
| **Marksheet & QR Verification** | `Implemented` | Official OpenPDF generation with ZXing QR code and public `/verify/result/*` route. |
| **Reports & Excel Bulk Tools** | `Implemented` | Student roster and teacher marks roster import/export via Apache POI. |
| **Institutional Notices** | `Implemented` | Broadcast announcements targeted to specific roles or campus-wide. |
| **Recycle Bin** | `Implemented` | Soft deletion and restoration for academic entities. |

---

## 17. Known Limitations

1. **Email SMTP Delivery:** If valid SMTP credentials are not configured in `application.properties` or environment variables, email dispatching logs a warning and gracefully falls back to in-app database notifications.
2. **Grading Rule Dependency:** An examination's results cannot be calculated unless grading rules have been defined for the institution. If no grading rules exist, `ResultCalculationService` defaults to standard percentage intervals.
3. **Credit Weighting:** SGPA is weighted by subject credits. If subject credits are zero or unset, SGPA computes as the arithmetic mean of subject grade points.
4. **Attendance:** Per institutional design requirements, attendance tracking and attendance modules are deliberately omitted from this project.

---

## 18. Known Cleanup Candidates

| Candidate Symbol / File | Reason & Evidence | Risk Level | Recommended Action |
|---|---|---|---|
| `import-students.sql` | Demo SQL file in project root. Not loaded by application runtime. | Safe | Keep as manual setup utility or archive. |
| `import-teachers.sql` | Demo SQL file in project root. Not loaded by application runtime. | Safe | Keep as manual setup utility or archive. |
| `import-subjects-v2.sql` | Demo SQL file in project root. Not loaded by application runtime. | Safe | Keep as manual setup utility or archive. |

---

## 19. AI Change Rules

1. **Read `brain.md` First:** Before making any code, schema, or configuration modification, read `brain.md` to understand existing contracts, patterns, and dependencies.
2. **Preserve Existing Functionality:** Never delete, disable, or alter the working behavior of existing features without explicit user instruction.
3. **Preserve Package & Naming Integrity:** Do not rename classes, packages (`com.intelliresult.nexus`), database tables, or column names.
4. **Enforce Jakarta EE Namespace:** Always import `jakarta.*` packages (`jakarta.servlet.*`, `jakarta.persistence.*`). Never introduce legacy `javax.*` imports.
5. **Maintain Session & Transaction Boundaries:**
   - Keep transactions exclusively inside the Service layer (`com.intelliresult.nexus.service.*`).
   - Never initiate or commit transactions inside Servlets, Filters, or JSPs.
   - Never close or unbind sessions manually in business logic; the `HibernateSessionFilter` owns the session lifecycle.
6. **Centralize Calculation Logic:** All marks, percentage, grade, SGPA, CGPA, and rank calculations must live exclusively in `ResultCalculationService` and `GradeUtil`. Never perform academic calculations in JSPs or controllers.
7. **Prevent IDOR & Access Control Vulnerabilities:**
   - In student controllers, always load student data from `currentUser.getId()`, never from request parameters.
   - In teacher controllers, always verify teacher assignment via `TeacherAssignmentService`.
8. **Never Trust Client Totals:** All totals and validations must be verified on the server side prior to database persistence.
9. **Maintain Documentation Integrity:** Do not create duplicate markdown documentation files. Keep `README.md` concise and update `brain.md` whenever architectural patterns, routes, or database schemas are altered.
10. **Verify with Full Build:** After modifying code, run `mvn clean test` to guarantee all 88 automated tests remain green.

---

## 20. Glossary

- **Academic Year:** Institutional calendar year (e.g. 2026-2027) designated as active or archived.
- **Semester:** Academic term (e.g. Semester 1 through 8) attached to a specific degree Course.
- **Section:** Grouping of students within a semester (e.g. Section A, Section B).
- **Subject:** Specific academic course module featuring theory, practical, and internal mark components and credits.
- **Teacher Subject Assignment:** Official link between a faculty member, a subject, and a class section authorizing marks entry.
- **Exam Status:** Lifecycle stage of an examination: `DRAFT` -> `SCHEDULED` -> `ACTIVE` -> `SUBMISSION` -> `UNDER_REVIEW` -> `PUBLISHED` -> `LOCKED`.
- **Result:** Subject-level record storing student obtained component marks, total marks, percentage, grade, grade point, and pass/fail standing.
- **Result Summary:** Precalculated examination-level record storing student total obtained marks, maximum marks, percentage, SGPA, cumulative CGPA, section class rank, and cohort rank.
- **Result History:** Immutable audit log tracking changes made to a student's marks during result corrections or revaluations.
- **Grading Rule:** Configurable percentage band defining minimum and maximum percentage, letter grade (e.g. A+), and grade point (e.g. 10.0).
- **SGPA (Semester Grade Point Average):** Credit-weighted average of grade points earned in a single examination/semester.
- **CGPA (Cumulative Grade Point Average):** Cumulative credit-weighted average across all completed semesters and examinations.
- **Revaluation Request:** Formal student petition requesting re-checking of an approved result.
- **Marksheet Verification:** Cryptographic token mechanism enabling public authentication of printed PDF marksheets via QR code.
- **Activity Log:** Audit trail recording administrative and security actions with IP address and timestamp.
- **Soft Delete:** Data protection pattern marking records as deleted (`deleted = true`) rather than physically removing rows from MySQL tables.


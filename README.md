# IntelliResult Nexus

A web-based Student Result Management System built with Java, Jakarta EE, Hibernate, MySQL, and JSP.

## Overview

IntelliResult Nexus is an enterprise academic result, examination, and student performance management system designed for colleges and universities. It centralizes and automates the entire academic lifecycle:
- **Academic Hierarchy:** Departments, courses, academic years, semesters, sections, and subjects.
- **Examination Management:** Scheduling, examination type management, and status lifecycle transitions.
- **Marks Processing:** Teacher marks entry (theory, practical, internal), draft saving, submission, and validation.
- **Automated Result Calculation:** Instant computation of totals, percentages, letter grades, grade points, SGPA, CGPA, class ranks, and pass/fail standing.
- **Verification & Marksheets:** Tamper-evident PDF marksheets with ZXing QR code verification for external validation.
- **Analytics & Notifications:** In-app alerts for scheduled exams and published results, accompanied by comparative performance analytics.

The platform provides tailored workspaces for three core user groups: **Administrators**, **Teachers**, and **Students**.

---

## Features

- **Role-Based Authentication & Authorization:** Multi-role access control (Admin, Teacher, Student) with BCrypt password hashing, session fixation protection, and CSRF defense.
- **Academic Structure Management:** Full administration of departments, degree courses, academic years, semesters, class sections, subjects, and teacher subject assignments.
- **Examination Lifecycle:** Create and advance exams through distinct phases: *DRAFT*, *SCHEDULED*, *ACTIVE*, *SUBMISSION*, *UNDER_REVIEW*, *PUBLISHED*, and *LOCKED*.
- **Teacher Marks Entry:** Two-step workflow enabling teachers to enter marks for theory, practical, and internal components, save drafts, and submit finalized marks for approval.
- **Result Approval & Publication Engine:** Admin review workflow with bulk publishing, bulk locking, and result correction capabilities backed by audit history.
- **Automated Result Computation:** Centralized evaluation engine calculating subject totals, component percentages, grade points, SGPA, cumulative CGPA, section class ranks, and cohort-wide ranks.
- **Comparative Performance Analytics:** Visual performance trends, class-average comparisons, section topper comparisons, and strength/weakness diagnostics using Chart.js.
- **QR-Verifiable PDF Marksheets:** Automated generation of official PDF marksheets with student details, component breakdown, SGPA, CGPA, institutional branding, authorized signature lines, and dynamic verification QR codes.
- **Public Verification Portal:** Public verification route (`/verify/result/{token}`) allowing employers and external parties to authenticate marksheet credentials against institutional records.
- **In-App Notification Engine:** Real-time event notifications for students and teachers upon exam scheduling, status changes, and result publications.
- **Revaluation System:** Student-initiated revaluation requests with administrative review, approval/rejection workflows, and automated result re-calculation.
- **Institutional Notices:** Broadcast notices for students, faculty, or the entire campus community.
- **Recycle Bin & Soft Deletion:** Safeguards against accidental data loss with soft deletion and restoration for academic entities.
- **Excel Bulk Processing:** Import students and batch-import/export teacher marks via Apache POI spreadsheets.

---

## Technology Stack

- **Java 17:** Core programming language utilizing modern records, pattern matching, and sealed types.
- **Jakarta EE 11 / Jakarta Servlet 6.1:** Core servlet specification running on modern Jakarta namespaces.
- **JSP 3.1 & JSTL 3.0:** Server-side templating with componentized header/footer layout fragments.
- **Hibernate ORM 7.4.5.Final:** Object-relational mapping using Jakarta Persistence annotations and ManagedSessionContext.
- **MySQL 8.0+:** Relational database with window functions (`RANK() OVER`), generated columns, and foreign keys.
- **HikariCP 7.1.0:** High-performance JDBC connection pooling.
- **Apache Tomcat 11.0.x:** Jakarta EE servlet container and HTTP web server.
- **OpenPDF 2.2.5:** Vector PDF document generation for official student marksheets.
- **ZXing 3.5.4:** QR code matrix generation for marksheet verification.
- **Apache POI 5.5.1:** Microsoft Excel (.xlsx) parsing and generation for bulk data operations.
- **Chart.js:** Responsive client-side data visualizations for performance analytics and dashboards.
- **Log4j2 2.26.0:** High-performance asynchronous application logging.
- **jBCrypt 0.4:** Secure salted password hashing.
- **Sentry Java SDK 8.28.0:** Optional real-time application error tracking and performance monitoring.

---

## Project Structure

```text
IntelliResult-Nexus/
├── pom.xml
├── README.md
├── brain.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/intelliresult/nexus/
│   │   │       ├── config/          # HibernateUtil, AppConfig
│   │   │       ├── controller/      # HTTP Servlets (Admin, Teacher, Student)
│   │   │       ├── dao/             # Data Access Objects and Hibernate implementations
│   │   │       ├── entity/          # JPA Entities and Mappings
│   │   │       ├── filter/          # HibernateSession, Auth, Authorization, CSRF
│   │   │       ├── listener/        # AppLifecycleListener (bootstrap & cleanup)
│   │   │       ├── service/         # Business logic, transactions, calculation engines
│   │   │       └── util/            # PDFUtil, QRCodeUtil, ExcelUtil, GradeUtil
│   │   ├── resources/
│   │   │   ├── application.properties
│   │   │   ├── hibernate.cfg.xml
│   │   │   ├── log4j2.xml
│   │   │   └── db/
│   │   │       ├── schema.sql       # Full DDL schema (24 tables)
│   │   │       └── seed-data.sql    # Optional demo data and seed users
│   │   └── webapp/
│   │       ├── admin/               # Admin management and dashboard JSPs
│   │       ├── teacher/             # Teacher marks-entry and dashboard JSPs
│   │       ├── student/             # Student dashboard, results, and analysis JSPs
│   │       ├── common/              # Shared error pages, access denied, layout fragments
│   │       ├── assets/              # CSS tokens, application shell styles, branding icons
│   │       └── WEB-INF/
│   │           └── web.xml          # Filter chain ordering and webapp configuration
│   └── test/
│       ├── java/                    # Automated integration and unit tests
│       └── resources/               # Test configuration (H2 in-memory profile)
```

---

## Requirements

- **Java Development Kit (JDK):** Version 17 or later (Eclipse Adoptium OpenJDK recommended)
- **Apache Maven:** Version 3.8 or later
- **MySQL Database Server:** Version 8.0 or later
- **Apache Tomcat:** Version 11.0.x (Jakarta EE 11 compatible)
- **Web Browser:** Google Chrome, Mozilla Firefox, Microsoft Edge, or Safari

---

## Database Setup

1. Log into your MySQL console or MySQL Workbench:
   ```bash
   mysql -u root -p
   ```

2. Create the database with UTF-8 character encoding:
   ```sql
   CREATE DATABASE intelliresult_nexus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

3. Import the database schema:
   ```bash
   mysql -u root -p intelliresult_nexus < src/main/resources/db/schema.sql
   ```

4. *(Optional)* Import demo seed data:
   ```bash
   mysql -u root -p intelliresult_nexus < src/main/resources/db/seed-data.sql
   ```

---

## Configuration

Database credentials and optional service integrations can be configured via `src/main/resources/application.properties` or overridden using environment variables at runtime.

### Environment Variables

| Variable | Description | Default / Example |
|---|---|---|
| `INTELLIRESULT_DB_URL` | JDBC database connection URL | `jdbc:mysql://localhost:3306/intelliresult_nexus?useSSL=false&serverTimezone=UTC` |
| `INTELLIRESULT_DB_USERNAME` | MySQL database username | `root` or `intelliresult_app` |
| `INTELLIRESULT_DB_PASSWORD` | MySQL database password | `YOUR_DATABASE_PASSWORD` |
| `INTELLIRESULT_MAIL_HOST` | *(Optional)* SMTP mail server host | `smtp.example.edu` |
| `INTELLIRESULT_MAIL_PORT` | *(Optional)* SMTP mail server port | `587` |
| `INTELLIRESULT_MAIL_USERNAME` | *(Optional)* SMTP username | `noreply@example.edu` |
| `INTELLIRESULT_MAIL_PASSWORD` | *(Optional)* SMTP password | `YOUR_SMTP_PASSWORD` |
| `INTELLIRESULT_SENTRY_DSN` | *(Optional)* Sentry error monitoring DSN | `YOUR_SENTRY_DSN` |

---

## Run the Project

### First-Time Run (Initial Setup & Launch)

Follow these steps when setting up and running the project for the very first time:

#### Step 1: Create the Database & Import Schema
Open your terminal or MySQL command-line client:
```bash
# 1. Log in to MySQL
mysql -u root -p

# 2. In MySQL prompt, create the database
CREATE DATABASE intelliresult_nexus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
exit;

# 3. Import the database schema (from the project directory)
mysql -u root -p intelliresult_nexus < src/main/resources/db/schema.sql

# 4. (Optional) Import demo seed data
mysql -u root -p intelliresult_nexus < src/main/resources/db/seed-data.sql
```

#### Step 2: Set Database Credentials
Set the environment variables in your terminal so Tomcat / the application can connect to MySQL:

**Windows PowerShell:**
```powershell
$env:INTELLIRESULT_DB_USERNAME="root"
$env:INTELLIRESULT_DB_PASSWORD="YOUR_DATABASE_PASSWORD"
```

**Windows Command Prompt (CMD):**
```cmd
set INTELLIRESULT_DB_USERNAME=root
set INTELLIRESULT_DB_PASSWORD=YOUR_DATABASE_PASSWORD
```

**Linux / macOS (Bash / Zsh):**
```bash
export INTELLIRESULT_DB_USERNAME="root"
export INTELLIRESULT_DB_PASSWORD="YOUR_DATABASE_PASSWORD"
```

#### Step 3: Build the Application
In the project root directory (where `pom.xml` is located), build the WAR archive:
```bash
mvn clean package
```
*(To build quickly skipping tests: `mvn clean package -DskipTests`)*.  
This produces the deployment file: `target/intelliresult-nexus.war`.

#### Step 4: Deploy WAR to Tomcat & Start Server
Copy the generated WAR file into your Apache Tomcat `webapps` directory and start the server:

**Windows PowerShell:**
```powershell
# Copy WAR to Tomcat webapps folder (adjust Tomcat path to match yours)
Copy-Item "target\intelliresult-nexus.war" "C:\path\to\apache-tomcat-11.0.x\webapps\" -Force

# Navigate to Tomcat bin directory and start
Set-Location "C:\path\to\apache-tomcat-11.0.x\bin"
.\catalina.bat run
```

**Linux / macOS:**
```bash
# Copy WAR to Tomcat webapps folder
cp target/intelliresult-nexus.war $CATALINA_HOME/webapps/

# Start Tomcat
$CATALINA_HOME/bin/catalina.sh run
```

#### Step 5: Open in Browser
Once Tomcat logs indicate `Deployment of web application archive [...intelliresult-nexus.war] has finished`, open:
👉 **[http://localhost:8080/intelliresult-nexus/](http://localhost:8080/intelliresult-nexus/)**

---

### Subsequent Runs (Starting the Project Again)

When you have already performed the first-time setup, you do not need to recreate the database or re-import the schema.

#### Scenario A: Running Again Without Code Changes
Simply ensure MySQL is running, set your environment variables, and start Tomcat:

**Windows PowerShell:**
```powershell
$env:INTELLIRESULT_DB_USERNAME="root"
$env:INTELLIRESULT_DB_PASSWORD="YOUR_DATABASE_PASSWORD"

Set-Location "C:\path\to\apache-tomcat-11.0.x\bin"
.\catalina.bat run
```

**Linux / macOS:**
```bash
export INTELLIRESULT_DB_USERNAME="root"
export INTELLIRESULT_DB_PASSWORD="YOUR_DATABASE_PASSWORD"

$CATALINA_HOME/bin/catalina.sh run
```
Open **[http://localhost:8080/intelliresult-nexus/](http://localhost:8080/intelliresult-nexus/)** in your browser.

#### Scenario B: Running Again After Making Code Changes
When you edit Java source files, JSPs, or configuration, re-package and copy the updated WAR:

**Windows PowerShell:**
```powershell
# 1. In project directory, rebuild the WAR
mvn package -DskipTests

# 2. Copy the updated WAR to Tomcat webapps (Tomcat will auto-reload the app)
Copy-Item "target\intelliresult-nexus.war" "C:\path\to\apache-tomcat-11.0.x\webapps\" -Force

# 3. If Tomcat is not already running, start it:
Set-Location "C:\path\to\apache-tomcat-11.0.x\bin"
.\catalina.bat run
```

**Linux / macOS:**
```bash
# 1. Rebuild the WAR
mvn package -DskipTests

# 2. Copy updated WAR
cp target/intelliresult-nexus.war $CATALINA_HOME/webapps/

# 3. If Tomcat is not running, start it:
$CATALINA_HOME/bin/catalina.sh run
```

---

## Demo Accounts

When seed data (`seed-data.sql`) is imported, the following default credentials are provided for testing:

| Role | Email / Identifier | Password | Description |
|---|---|---|---|
| **Admin** | `admin@yourcollege.edu` | `Admin@123` | Full administrative institutional access |
| **Teacher** | `jaykishandasgdg2024@gmail.com` | `Admin@123` | Faculty member assigned to Semester 5 CSE subjects |
| **Student** | `riya.nair.0561@example.edu` | `Admin@123` | Student enrolled in Semester 5 CSE Section A |

> [!NOTE]
> All default demo accounts are for local testing and evaluation only. Always change passwords or remove test accounts prior to any production deployment.

---

## User Roles

### Admin
- Manage academic structure: Departments, courses, academic years, semesters, sections, and subjects.
- Manage users: Create, update, deactivate, reset passwords, and assign teacher subjects.
- Manage examinations: Create exams, define maximum and passing marks, set grading rules, and manage statuses.
- Oversee results: Review teacher submissions, approve results, publish results to students, lock finalized results, and audit correction history.
- Handle revaluations, system settings, activity audit logs, and institutional notice broadcasts.

### Teacher
- View assigned classes, subjects, enrolled student rosters, and scheduled examinations.
- Enter component marks (theory, practical, internal) for students in assigned classes.
- Save progress as drafts and submit complete marks rosters for administrative review.
- Track draft and submitted results, view section performance statistics, and monitor notifications.

### Student
- Access personal dashboard showing current percentage, SGPA, cumulative CGPA, grade, and class rank.
- View published examination results and subject component breakdowns.
- Access comparative analytics (personal score vs. class average and section topper).
- Download official PDF marksheets equipped with authenticity verification QR codes.
- Request re-evaluation for eligible examination results and receive scheduling notifications.

---

## Main Workflows

### Exam Workflow
1. Admin creates an examination with date, session, and associated academic semester.
2. Admin configures subject mark components (theory, practical, internal) and grading rules.
3. Admin schedules and activates the examination; relevant teachers and students receive in-app notifications.
4. Teachers enter student marks in their portal and submit them for administrative review.
5. Admin approves and publishes the results, notifying all participating students and teachers.

### Result Workflow
1. Teacher navigates to Marks Entry, selects an active examination and assigned subject/section, and enters component scores.
2. Teacher saves work as a draft or performs a full submission.
3. System verifies validation constraints (e.g. scores cannot exceed maximum marks).
4. Admin opens Result Approvals, inspects submitted rosters, and triggers approval/publication.
5. `ResultCalculationService` computes totals, grades, grade points, SGPA, CGPA, section class ranks, and cohort ranks.
6. Calculated results and QR-verifiable marksheets become immediately accessible to students.

### Revaluation Workflow
1. Student views an eligible published result and clicks "Request Re-evaluation" with a justification note.
2. System logs the revaluation request under `revaluation_requests`.
3. Admin reviews pending requests in the Admin Revaluation queue and chooses to Approve or Reject.
4. If approved and marks are revised, `ResultService` records the modification in `result_history`, recalculates the affected student's summary, and recalculates section ranks.
5. An automated notification updates the student regarding the revaluation outcome.

---

## Screenshots

| Page / Feature | Description | Screenshot File |
|---|---|---|
| **Login Portal** | Clean branded authentication with role routing | `screenshots/01_login_page.png` |
| **Admin Dashboard** | Institutional KPI cards, pass/fail trends, recent activity | `screenshots/02_admin_dashboard.png` |
| **Academic Setup** | Hub managing departments, courses, semesters, and sections | `screenshots/03_academic_setup.png` |
| **Exam Management** | Lifecycle tracking (Scheduled, Active, Published, Locked) | `screenshots/04_admin_exam_management.png` |
| **Result Approvals** | Administrative review, bulk publishing, and locking | `screenshots/05_admin_result_approvals.png` |
| **Student Performance Directory** | Cohort performance overview with Latest SGPA and CGPA | `screenshots/06_admin_student_performance_overview.png` |
| **Student Detailed Profile** | Exam-by-exam component breakdown, strengths/weaknesses | `screenshots/07_admin_student_detail_profile.png` |
| **Teacher Dashboard** | Faculty workload, assigned classes, and pending marks | `screenshots/08_teacher_dashboard.png` |
| **Teacher Marks Entry Grid** | Live component marks entry (Theory, Practical, Internal) | `screenshots/09_teacher_marks_entry_grid.png` |
| **Teacher Student Performance** | Faculty view of assigned class standing and rankings | `screenshots/10_teacher_student_performance.png` |
| **Student Dashboard** | Academic overview cards (SGPA, CGPA, Rank) & upcoming exams | `screenshots/11_student_dashboard.png` |
| **Student Results Page** | Full component marks, grades, SGPA, CGPA, and marksheet link | `screenshots/12_student_results_page.png` |
| **Performance Analytics** | Comparative charts (vs. Class Average, Topper, Subjects) | `screenshots/13_student_performance_analysis.png` |
| **Marksheet Center** | Official marksheet download with QR verification | `screenshots/14_student_marksheet_center.png` |
| **Student Profile** | Institutional registration, SGPA, CGPA, and contact info | `screenshots/15_student_profile.png` |
| **Public QR Verification** | Real-time digital credential verification certificate | `screenshots/16_public_qr_verification.png` |

---

## License

This project is licensed under the MIT License.

---

## Important Notes

- **Authentication & Sessions:** User sessions timeout after 30 minutes of inactivity (`app.session.timeoutMinutes=30`).
- **Database Safety:** Ensure MySQL character encoding is set to `utf8mb4` for Unicode and special character support.
- **Production Deployment:** In production environments, always set `app.environment=production`, enforce HTTPS reverse proxy termination, and inject database credentials via secure environment variables.

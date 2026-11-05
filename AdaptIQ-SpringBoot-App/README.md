# AdaptIQ – AI Adaptive Learning System (Spring Boot)

A web application that adapts learning content for **university students with disabilities in South Africa**.
It implements the problem statement and the System Requirements Document (FR-01 – FR-12) for the capstone project
*"Design and Implementation of Adaptive Learning Technologies Using AI for Students with Disabilities in South African Universities"*,
and extends the original repository structure (`controller → services → repositories`, creational patterns in `patterns/`).

## Run it

Requirements: **JDK 17+** and **Maven 3.8+**.

```bash
mvn spring-boot:run
# open http://localhost:8080          (web app)
# open http://localhost:8080/swagger-ui.html   (interactive API docs, OpenAPI 3)
mvn test                                     (unit tests)
```

Demo data is loaded at start-up (`adaptiq.seed-demo-data=true`). **All demo accounts use the password `Passw0rd!`.**

| Account | Role | Story |
|---|---|---|
| thandi@student.ac.za | Student (visual) | Gets only content with alt text / audio description; defaults to read-aloud |
| sipho@student.ac.za | Student (hearing) | Never offered video without captions; defaults to captions |
| lerato@student.ac.za | Student (motor) | Never offered fine-motor interactive content |
| ayanda@student.ac.za | Student (learning disability) | Scores dropped 38% → **early-difficulty alert**; feedback shown in simplified text |
| neo@student.ac.za | Student (none) | Finished every module |
| zanele@student.ac.za | Student (none) | Behind schedule → **at-risk**, inactivity alert |
| educator@adaptiq.ac.za | Educator | Class dashboard, alerts, feedback, content upload |
| dso@adaptiq.ac.za | Disability support officer | Dashboards, alerts, reports |
| admin@adaptiq.ac.za | Administrator | Everything + users + reports |
| engineer@adaptiq.ac.za | ML engineer | Model monitor, bias audit, retraining |

Change `adaptiq.seed-demo-data=false` before any real use.

## What is implemented (by requirement)

| Req | Implementation | Where |
|---|---|---|
| FR-01 | Register/login, PBKDF2 password hashing, **lock after 5 failed attempts**, bearer-token sessions, admin unlock | `AuthService`, `AuthController` |
| FR-02 | Learning profile: disability categories, assistive tech, preferred formats, goals, extra time | `ProfileService` |
| FR-03 | Hybrid recommender: hard accessibility filter → `wSim·peer-similarity + wPref·preference`, difficulty fit, score clamped 0–100, ≥3 results, 👍/👎 ratings, refresh on completion | `RecommendationService` |
| FR-04 | Content as text, **simplified text** (Flesch-Kincaid grade shown), **read-aloud audio script**, **timed captions**, high-contrast themes | `ContentAdaptationService` |
| FR-05 | Student dashboard: completion vs expected, modules, score timeline with date range, AI insights, keyboard/screen-reader friendly | `DashboardService` + UI |
| FR-06 | Educator dashboard with disability / progress / course / at-risk filters and per-student activity log | `DashboardService` |
| FR-07 | Alerts when last-3 average falls **>15 % below personal baseline** (or 14 days inactive); in-app + e-mail (logged); acknowledge / dismiss / escalate | `AlertService` |
| FR-08 | Text / audio (≤ 300 s, transcript required) / file feedback, rendered per student's profile, notification, acknowledge + reply | `FeedbackService` |
| FR-09 | 6 themes (incl. 2 high-contrast), text size, colour-vision filters, reduce motion; applied instantly, persisted server-side | `ProfileService`, `styles.css` |
| FR-10 | Upload with automated WCAG-style gate (captions, transcripts, alt text, file types); blocked with HTTP 422 unless admin override | `AccessibilityValidator`, `ContentService` |
| FR-11 | Platform report (engagement, compliance, at-risk, accommodation usage) as JSON, **CSV and PDF** | `ReportService`, `SimplePdf` |
| FR-12 | Model metrics, per-category **bias audit (5-point threshold)**, retraining (weight calibration) with versioned changelog | `ModelMonitoringService` |
| NFR-SEC2 | Role-based access control on every endpoint (`@Require`), students limited to their own data | `security/` |
| NFR-M1 | OpenAPI 3 + Swagger UI | springdoc |

At-risk rule: a student is *at risk* when completion is below **50 % of the expected progress** for the elapsed part of the semester
(`adaptiq.semester.total-weeks` / `elapsed-weeks`).

## Project layout

```
src/main/java/com/aiadaptive
  Application.java
  domain/        entities + enums
  repositories/  Repository<T,ID> interface, entity repositories, inMemory/ implementations
  services/      business logic (one class per requirement area)
  controller/    REST controllers
  security/      token interceptor, @Require / @Public, AuthContext
  dto/           request/response records
  config/        WebConfig, DataSeeder
  patterns/      original creational-pattern examples (unchanged)
src/main/resources/static   accessible single-page UI (index.html, styles.css, app.js)
src/test/java/tests         unit tests (services wired by hand, no Spring context needed)
```

## Honest limitations (read before using with real students)

* **Storage is in-memory** – data is lost on restart. The repository interfaces are the seam for a JPA/PostgreSQL implementation (`RepositoryFactory`).
* **No university SSO or MFA yet** (FR-01 acceptance criteria mention both). Local accounts only; sessions are in memory.
* **The "AI" is transparent and rule-based**: collaborative/content-based scoring, rule-based plain-language simplification, browser text-to-speech.
  Dense technical text often stays above Grade 8 (e.g. the seeded database lesson goes 15.2 → 10.0), as noted in the testing phase (DEF-004).
  There is no server-side speech-to-text; captions are produced from supplied transcripts.
* **Uploads take metadata + text/transcript, not binary files**; file name extensions are validated.
* E-mail is simulated (log line + `EMAIL` notification). Encryption at rest (NFR-SEC1), HTTPS and POPIA/GDPR controls must be added at deployment.
* The seeded recommendation ratings are simulated so the model monitor has data; the bias alert you see on first start is produced by that random data.

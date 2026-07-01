# Adaptive College Scheduling Engine

Desktop JavaFX implementation of the ACSE brief. The app uses a clean layered shape:

- `ui`: JavaFX screens and controllers.
- `service`: scheduler, auth, exams, setup workflow.
- `repository`: in-memory data store.
- `domain`: scheduling entities and constraints.
- `infrastructure`: seed data and contract exports.

## Run

With Gradle installed, run:

```powershell
gradle run
```

Or with Maven installed, run:

```powershell
mvn javafx:run
```

## What is included

- Unified login for Admin, Teacher, and Student.
- Google Calendar style week grid from Monday to Saturday.
- Admin dashboard with room occupancy, schedule filters, auto-scheduler trigger, and conflict suggestions.
- Teacher and student personal schedule views.
- CSP scheduler with MRV ordering, teacher availability, room/entity conflicts, capacity, daily load, duplicate-session checks, lab-group independence, and soft warning scoring.
- Exam scheduling service with open-space replacement and displacement escalation levels A, B, and C.
- SQL migration and OpenAPI contract artifacts in `contracts/`.

Demo accounts:

- Admin: `admin@acse.local`
- Student: `student@acse.local`

Any password is accepted in this desktop demo.

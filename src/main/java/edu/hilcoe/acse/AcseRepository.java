package edu.hilcoe.acse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AcseRepository {
    final List<User> users = new ArrayList<>();
    final List<Teacher> teachers = new ArrayList<>();
    final List<Room> rooms = new ArrayList<>();
    final List<Program> programs = new ArrayList<>();
    final List<Batch> batches = new ArrayList<>();
    final List<Section> sections = new ArrayList<>();
    final List<LabGroup> labGroups = new ArrayList<>();
    final List<Course> courses = new ArrayList<>();
    final List<CourseOffering> offerings = new ArrayList<>();
    final List<StudentProfile> studentProfiles = new ArrayList<>();
    final List<ScheduleItem> schedule = new ArrayList<>();
    final List<Exam> exams = new ArrayList<>();
    final List<ExamAssignment> examAssignments = new ArrayList<>();
    final List<String> lastSuggestions = new ArrayList<>();
    final List<String> lastWarnings = new ArrayList<>();

    Optional<User> findUserByEmail(String email) {
        return users.stream().filter(user -> user.email().equalsIgnoreCase(email)).findFirst();
    }

    Program findOrCreateProgram(String academicYear, SemesterPeriod semester) {
        return programs.stream()
                .filter(program -> program.academicYear().equals(academicYear) && program.semester() == semester)
                .findFirst()
                .orElseGet(() -> {
                    String label = semester == SemesterPeriod.FIRST ? "First" : "Second";
                    Program program = new Program(
                            Ids.next(),
                            academicYear + " " + label + " Semester",
                            semester,
                            academicYear,
                            LocalDate.now(),
                            LocalDate.now().plusMonths(semester == SemesterPeriod.FIRST ? 4 : 5),
                            semester == SemesterPeriod.FIRST ? 16 : 20,
                            5,
                            6
                    );
                    programs.add(program);
                    return program;
                });
    }

    List<Batch> batchesForProgram(String programId) {
        return batches.stream().filter(batch -> batch.programId().equals(programId)).toList();
    }

    List<CourseOffering> offeringsForBatch(String batchId) {
        return offerings.stream().filter(offering -> offering.batchId().equals(batchId)).toList();
    }

    List<CourseOffering> offeringsMissingTeachers() {
        return offerings.stream().filter(offering -> offering.lecturerId() == null).toList();
    }

    Optional<Teacher> teacherByUser(String userId) {
        return teachers.stream().filter(teacher -> teacher.userId().equals(userId)).findFirst();
    }

    Teacher teacher(String id) {
        return teachers.stream().filter(teacher -> teacher.id().equals(id)).findFirst().orElseThrow();
    }

    Room room(String id) {
        return rooms.stream().filter(room -> room.id().equals(id)).findFirst().orElseThrow();
    }

    Course course(String id) {
        return courses.stream().filter(course -> course.id().equals(id)).findFirst().orElseThrow();
    }

    CourseOffering offering(String id) {
        return offerings.stream().filter(offering -> offering.id().equals(id)).findFirst().orElseThrow();
    }

    Batch batch(String id) {
        return batches.stream().filter(batch -> batch.id().equals(id)).findFirst().orElseThrow();
    }

    Program program(String id) {
        return programs.stream().filter(program -> program.id().equals(id)).findFirst().orElseThrow();
    }

    Section section(String id) {
        return sections.stream().filter(section -> section.id().equals(id)).findFirst().orElseThrow();
    }

    LabGroup labGroup(String id) {
        return labGroups.stream().filter(group -> group.id().equals(id)).findFirst().orElseThrow();
    }

    String audience(ScheduleItem item) {
        if (item.kind() == SessionKind.EXAM) {
            return program(exams.stream().filter(exam -> exam.id().equals(item.offeringId())).findFirst().orElseThrow().programId()).name();
        }
        if (item.kind() == SessionKind.THEORY) {
            Section section = section(item.sectionId());
            return batch(section.batchId()).name() + " / " + section.name();
        }
        LabGroup group = labGroup(item.labGroupId());
        Section section = section(group.sectionId());
        return batch(section.batchId()).name() + " / " + section.name() + " / " + group.name();
    }

    List<CalendarEvent> calendarEvents(List<ScheduleItem> items) {
        List<CalendarEvent> events = new ArrayList<>();
        for (ScheduleItem item : items.stream().sorted(Comparator.comparing(ScheduleItem::day).thenComparing(i -> i.slot().start())).toList()) {
            Course course = course(item.courseId());
            Room room = room(item.roomId());
            String teacher = item.kind() == SessionKind.EXAM ? "Exam Board" : teacher(item.teacherId()).name();
            String color = switch (item.kind()) {
                case THEORY -> "#2563eb";
                case LAB -> "#059669";
                case EXAM -> "#dc2626";
            };
            events.add(new CalendarEvent(
                    item.id(),
                    course.name() + " " + item.kind(),
                    item.day(),
                    item.slot().id(),
                    item.slot().start().toString(),
                    item.slot().end().toString(),
                    room.name(),
                    teacher,
                    audience(item),
                    item.kind(),
                    color
            ));
        }
        return events;
    }

    CalendarPayload payloadFor(List<ScheduleItem> items) {
        return new CalendarPayload(LocalDate.now().with(DayOfWeek.MONDAY), Week.TEACHING_DAYS, TimeSlot.grid(),
                calendarEvents(items), List.copyOf(lastWarnings));
    }

    Map<String, Integer> roomOccupancy() {
        Map<String, Integer> occupancy = new HashMap<>();
        for (Room room : rooms) {
            occupancy.put(room.name(), 0);
        }
        for (ScheduleItem item : schedule) {
            Room room = room(item.roomId());
            occupancy.put(room.name(), occupancy.getOrDefault(room.name(), 0) + 1);
        }
        return occupancy;
    }
}

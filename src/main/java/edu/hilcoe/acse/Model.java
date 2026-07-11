package edu.hilcoe.acse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

enum UserRole { ADMIN, TEACHER, STUDENT }
enum SemesterPeriod { FIRST, SECOND }
enum TeacherRole { LECTURER, LAB_INSTRUCTOR }
enum RoomType { LECTURE_ROOM, COMPUTER_LAB, LIBRARY }
enum Equipment { TV_BOARD, BOARD_ONLY, COMPUTERS, LIBRARY }
enum Block { MORNING, AFTERNOON }
enum SessionKind { THEORY, LAB, EXAM }

record TimeSlot(String id, LocalTime start, LocalTime end, Block block) {
    static List<TimeSlot> grid() {
        return List.of(
                new TimeSlot("M1", LocalTime.of(8, 0), LocalTime.of(9, 30), Block.MORNING),
                new TimeSlot("M2", LocalTime.of(9, 45), LocalTime.of(11, 15), Block.MORNING),
                new TimeSlot("M3", LocalTime.of(11, 30), LocalTime.of(13, 0), Block.MORNING),
                new TimeSlot("A1", LocalTime.of(14, 0), LocalTime.of(15, 30), Block.AFTERNOON),
                new TimeSlot("A2", LocalTime.of(15, 45), LocalTime.of(17, 15), Block.AFTERNOON)
        );
    }
}

record User(String id, UserRole role, String name, String email, String passwordHash) {
    static User of(UserRole role, String name, String email) {
        return new User(UUID.randomUUID().toString(), role, name, email, "desktop-demo");
    }
}

record Teacher(String id, String userId, String name, TeacherRole role, Map<DayOfWeek, Set<String>> availability) {
    boolean isAvailable(DayOfWeek day, TimeSlot slot) {
        return availability.getOrDefault(day, Set.of()).contains(slot.id());
    }
}

record Room(String id, String name, int capacity, RoomType type, Equipment equipment) { }

record Program(String id, String name, SemesterPeriod semester, String academicYear, LocalDate startDate, LocalDate endDate,
               int totalWeeks, int typicalMinCourses, int typicalMaxCourses) {
    String displayLabel() {
        return academicYear + " — " + (semester == SemesterPeriod.FIRST ? "First" : "Second") + " Semester";
    }
}

record Batch(String id, String programId, String name, int studentCount) { }
record Section(String id, String batchId, String name, int size) { }
record LabGroup(String id, String sectionId, String name, int size) { }

record Course(String id, String code, String name, boolean requiresLab, boolean needsTv, int weeklyTheoryCount, int weeklyLabCount) {
    static Course create(String id, String code, String name, boolean requiresLab, boolean needsTv) {
        return new Course(id, code, name, requiresLab, needsTv, 2, requiresLab ? 1 : 0);
    }
}

record CourseOffering(String id, String batchId, String courseId, String lecturerId, String labInstructorId) { }

record StudentProfile(String userId, String batchId, String sectionId, String labGroupId) { }

record Candidate(DayOfWeek day, TimeSlot slot, Room room, int score, List<String> warnings) { }

record ClassInstance(String id, String offeringId, String courseId, SessionKind kind, String sectionId, String labGroupId,
                     String teacherId, int size, boolean needsTv) {
    String entityId() {
        return kind == SessionKind.THEORY ? sectionId : labGroupId;
    }
}

record ScheduleItem(String id, String offeringId, String courseId, String sectionId, String labGroupId, String roomId,
                    String teacherId, DayOfWeek day, TimeSlot slot, SessionKind kind, List<String> warnings) {
    boolean overlaps(ScheduleItem other) {
        return day == other.day && slot.id().equals(other.slot.id());
    }
}

record Exam(String id, String courseId, String programId, LocalTime start, LocalTime end) { }

record ExamAssignment(String id, String examId, String studentId, String roomId, String seatNumber) { }

record SolverResult(List<ScheduleItem> schedule, List<String> suggestions, List<String> warnings) {
    boolean success() {
        return suggestions.isEmpty();
    }
}

record CalendarEvent(String id, String title, DayOfWeek day, String slotId, String start, String end, String room,
                     String teacher, String audience, SessionKind kind, String color) { }

record CalendarPayload(LocalDate weekStart, List<DayOfWeek> days, List<TimeSlot> slots, List<CalendarEvent> events,
                       List<String> warnings) { }

final class Ids {
    private Ids() { }

    static String next() {
        return UUID.randomUUID().toString();
    }
}

final class Week {
    static final List<DayOfWeek> TEACHING_DAYS = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
    );

    private Week() { }
}

final class Availability {
    private Availability() { }

    static Map<DayOfWeek, Set<String>> allTeachingSlots() {
        Map<DayOfWeek, Set<String>> map = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : Week.TEACHING_DAYS) {
            map.put(day, Set.of("M1", "M2", "M3", "A1", "A2"));
        }
        return map;
    }

    static Map<DayOfWeek, Set<String>> morningsOnly() {
        Map<DayOfWeek, Set<String>> map = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : Week.TEACHING_DAYS) {
            map.put(day, Set.of("M1", "M2", "M3"));
        }
        return map;
    }
}

final class ScheduleRules {
    private ScheduleRules() { }

    static boolean sameEntity(ScheduleItem item, ClassInstance instance) {
        if (item.kind() == SessionKind.EXAM) {
            return false;
        }
        if (instance.kind() == SessionKind.THEORY) {
            return Objects.equals(instance.sectionId(), item.sectionId());
        }
        if (item.kind() == SessionKind.THEORY) {
            return Objects.equals(instance.sectionId(), item.sectionId());
        }
        return Objects.equals(instance.labGroupId(), item.labGroupId());
    }

    static boolean sameCourseForSameEntity(ScheduleItem item, ClassInstance instance) {
        return sameEntity(item, instance) && item.courseId().equals(instance.courseId());
    }

    static int dailyLoad(List<ScheduleItem> items, ClassInstance instance, DayOfWeek day) {
        int load = 0;
        for (ScheduleItem item : items) {
            if (item.day() == day && sameEntity(item, instance)) {
                load++;
            }
        }
        return load;
    }

    static List<String> teacherBlockWarnings(List<ScheduleItem> items) {
        List<String> warnings = new ArrayList<>();
        Map<String, Map<DayOfWeek, Set<Block>>> blocksByTeacher = new java.util.HashMap<>();
        for (ScheduleItem item : items) {
            if (item.kind() == SessionKind.EXAM) {
                continue;
            }
            blocksByTeacher
                    .computeIfAbsent(item.teacherId(), ignored -> new EnumMap<>(DayOfWeek.class))
                    .computeIfAbsent(item.day(), ignored -> new java.util.HashSet<>())
                    .add(item.slot().block());
        }
        blocksByTeacher.forEach((teacher, byDay) -> byDay.forEach((day, blocks) -> {
            if (blocks.size() > 1) {
                warnings.add("Teacher " + teacher + " has mixed morning/afternoon classes on " + day + ".");
            }
        }));
        return warnings;
    }
}

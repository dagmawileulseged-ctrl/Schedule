package edu.hilcoe.acse;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SeedData {
    private SeedData() { }

    static AcseRepository create() {
        AcseRepository repo = new AcseRepository();

        User admin = User.of(UserRole.ADMIN, "ACSE Admin", "admin@acse.local");
        User student = User.of(UserRole.STUDENT, "Student Demo", "student@acse.local");
        repo.users.add(admin);
        repo.users.add(student);

        addLectureRooms(repo, "201", "202", "203", "301", "302", "303", "401", "402", "601");
        addLabs(repo, "204", "304", "404", "504");
        addDemoAcademicData(repo);

        return repo;
    }

    private static void addLectureRooms(AcseRepository repo, String... names) {
        for (String name : names) {
            int capacity = name.equals("601") ? 80 : 45;
            Equipment equipment = name.endsWith("01") || name.endsWith("03") ? Equipment.TV_BOARD : Equipment.BOARD_ONLY;
            repo.rooms.add(new Room(Ids.next(), name, capacity, RoomType.LECTURE_ROOM, equipment));
        }
    }

    private static void addLabs(AcseRepository repo, String... names) {
        for (String name : names) {
            repo.rooms.add(new Room(Ids.next(), "Lab " + name, 24, RoomType.COMPUTER_LAB, Equipment.COMPUTERS));
        }
    }

    static Batch addBatch(AcseRepository repo, Program program, String name, List<String> sectionNames) {
        int sectionSize = sectionNames.size() == 1 ? 40 : 35;
        Batch batch = new Batch(Ids.next(), program.id(), name, sectionSize * sectionNames.size());
        repo.batches.add(batch);
        int groupNumber = 1;
        for (String sectionName : sectionNames) {
            Section section = new Section(Ids.next(), batch.id(), sectionName, sectionSize);
            repo.sections.add(section);
            int firstGroupSize = (int) Math.ceil(sectionSize / 2.0);
            repo.labGroups.add(new LabGroup(Ids.next(), section.id(), "G" + groupNumber++, firstGroupSize));
            repo.labGroups.add(new LabGroup(Ids.next(), section.id(), "G" + groupNumber++, sectionSize - firstGroupSize));
        }
        return batch;
    }

    private static void addDemoAcademicData(AcseRepository repo) {
        Program program = repo.findOrCreateProgram("2026/27", SemesterPeriod.FIRST);
        Batch batch = addBatch(repo, program, "DRBSE2502", List.of("Section A", "Section B"));

        addOffering(repo, batch, "SE2222", "Web", true, true,
                teacher(repo, "Kibrom", TeacherRole.LECTURER, morning(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
                teacher(repo, "Abelti", TeacherRole.LAB_INSTRUCTOR, morning(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)));
        addOffering(repo, batch, "SE1221", "OOP", true, true,
                teacher(repo, "Nesredin", TeacherRole.LECTURER, morning(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)),
                teacher(repo, "Betsi", TeacherRole.LAB_INSTRUCTOR, mixed(
                        DayOfWeek.TUESDAY, Set.of("M1", "M2", "M3", "A1", "A2"),
                        DayOfWeek.THURSDAY, Set.of("M1", "M2", "M3"))));
        addOffering(repo, batch, "CC2131", "Math", false, true,
                teacher(repo, "Gech", TeacherRole.LECTURER, morning(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
                null);
        addOffering(repo, batch, "CC0193", "Econ", false, true,
                teacher(repo, "Tewlde", TeacherRole.LECTURER, mixed(
                        DayOfWeek.MONDAY, Set.of("M1", "M2", "M3"),
                        DayOfWeek.SATURDAY, Set.of("M1", "M2", "M3"),
                        DayOfWeek.THURSDAY, Set.of("A1", "A2"),
                        DayOfWeek.FRIDAY, Set.of("A1", "A2"))),
                null);
        addOffering(repo, batch, "CC0197", "Global", false, true,
                teacher(repo, "Yirga", TeacherRole.LECTURER, afternoon(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
                null);
    }

    private static void addOffering(AcseRepository repo, Batch batch, String code, String name, boolean requiresLab,
                                    boolean needsTv, Teacher lecturer, Teacher labInstructor) {
        Course course = Course.create(Ids.next(), code, name, requiresLab, needsTv);
        repo.courses.add(course);
        repo.offerings.add(new CourseOffering(Ids.next(), batch.id(), course.id(), lecturer.id(),
                labInstructor == null ? null : labInstructor.id()));
    }

    private static Teacher teacher(AcseRepository repo, String name, TeacherRole role, Map<DayOfWeek, Set<String>> availability) {
        User user = User.of(UserRole.TEACHER, name, teacherEmail(name));
        Teacher teacher = new Teacher(Ids.next(), user.id(), name, role, availability);
        repo.users.add(user);
        repo.teachers.add(teacher);
        return teacher;
    }

    private static String teacherEmail(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", ".") + "@acse.local";
    }

    private static Map<DayOfWeek, Set<String>> morning(DayOfWeek... days) {
        Map<DayOfWeek, Set<String>> availability = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : days) {
            availability.put(day, Set.of("M1", "M2", "M3"));
        }
        return availability;
    }

    private static Map<DayOfWeek, Set<String>> afternoon(DayOfWeek... days) {
        Map<DayOfWeek, Set<String>> availability = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : days) {
            availability.put(day, Set.of("A1", "A2"));
        }
        return availability;
    }

    private static Map<DayOfWeek, Set<String>> mixed(Object... entries) {
        Map<DayOfWeek, Set<String>> availability = new EnumMap<>(DayOfWeek.class);
        for (int i = 0; i < entries.length; i += 2) {
            availability.put((DayOfWeek) entries[i], (Set<String>) entries[i + 1]);
        }
        return availability;
    }

}

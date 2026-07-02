package edu.hilcoe.acse;

import java.util.List;

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

}

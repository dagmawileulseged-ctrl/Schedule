package edu.hilcoe.acse;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ExamService {
    private final AcseRepository repo;
    private final ScheduleEngine engine;

    ExamService(AcseRepository repo, ScheduleEngine engine) {
        this.repo = repo;
        this.engine = engine;
    }

    List<String> scheduleExam(String courseId, String programId, DayOfWeek day, LocalTime start, LocalTime end) {
        Exam exam = new Exam(Ids.next(), courseId, programId, start, end);
        repo.exams.add(exam);
        List<String> log = new ArrayList<>();
        Program program = repo.program(programId);

        List<ScheduleItem> released = repo.schedule.stream()
                .filter(item -> item.kind() != SessionKind.EXAM)
                .filter(item -> repo.program(repo.batch(repo.offering(item.offeringId()).batchId()).programId()).id().equals(programId))
                .toList();
        repo.schedule.removeAll(released);
        log.add("Exam week opened " + released.size() + " regular " + program.semester() + " semester slots for " + program.name() + ".");

        List<Room> examRooms = repo.rooms.stream()
                .filter(room -> room.type() == RoomType.LECTURE_ROOM || room.type() == RoomType.COMPUTER_LAB || room.type() == RoomType.LIBRARY)
                .sorted(Comparator.comparing(Room::capacity).reversed())
                .toList();
        int students = repo.batches.stream()
                .filter(batch -> batch.programId().equals(programId))
                .mapToInt(Batch::studentCount)
                .sum();
        int remaining = students;
        TimeSlot virtualSlot = new TimeSlot("EXAM", start, end, start.isBefore(LocalTime.NOON) ? Block.MORNING : Block.AFTERNOON);
        for (Room room : examRooms) {
            if (remaining <= 0) {
                break;
            }
            repo.schedule.add(new ScheduleItem(Ids.next(), exam.id(), courseId, null, null, room.id(), "EXAM_BOARD",
                    day, virtualSlot, SessionKind.EXAM, List.of()));
            remaining -= room.capacity();
        }
        if (remaining > 0) {
            log.add("Warning: available rooms are short by " + remaining + " seats.");
        }

        List<ScheduleItem> displaced = repo.schedule.stream()
                .filter(item -> item.kind() != SessionKind.EXAM)
                .filter(item -> item.day() == day)
                .filter(item -> overlaps(item.slot().start(), item.slot().end(), start, end))
                .toList();

        for (ScheduleItem item : displaced) {
            if (sameDayShift(item, log) || weekShift(item, log)) {
                continue;
            }
            SolverResult result = engine.generate();
            log.add("Level C full week re-optimization triggered for " + repo.course(item.courseId()).name()
                    + "; success=" + result.success() + ".");
        }
        repo.lastWarnings.addAll(log);
        return log;
    }

    private boolean sameDayShift(ScheduleItem item, List<String> log) {
        for (TimeSlot slot : TimeSlot.grid()) {
            for (Room room : repo.rooms) {
                if (engine.canPlace(item, item.day(), slot, room, repo.schedule)) {
                    move(item, item.day(), slot, room);
                    log.add("Level A same-day shift applied to " + repo.course(item.courseId()).name()
                            + " at " + item.day() + " " + slot.id() + ".");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean weekShift(ScheduleItem item, List<String> log) {
        for (DayOfWeek day : Week.TEACHING_DAYS) {
            if (day == item.day()) {
                continue;
            }
            for (TimeSlot slot : TimeSlot.grid()) {
                for (Room room : repo.rooms) {
                    if (engine.canPlace(item, day, slot, room, repo.schedule)) {
                        move(item, day, slot, room);
                        log.add("Level B week shift applied to " + repo.course(item.courseId()).name()
                                + " on " + day + " " + slot.id() + ".");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void move(ScheduleItem item, DayOfWeek day, TimeSlot slot, Room room) {
        repo.schedule.remove(item);
        repo.schedule.add(new ScheduleItem(item.id(), item.offeringId(), item.courseId(), item.sectionId(), item.labGroupId(),
                room.id(), item.teacherId(), day, slot, item.kind(), item.warnings()));
    }

    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}

package edu.hilcoe.acse;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class ScheduleEngine {
    private final AcseRepository repo;

    ScheduleEngine(AcseRepository repo) {
        this.repo = repo;
    }

    SolverResult generate() {
        List<ClassInstance> instances = buildClassInstances();
        List<ScheduleItem> placed = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        while (!instances.isEmpty()) {
            ClassInstance next = instances.stream()
                    .min(Comparator.comparingInt(instance -> validCandidates(instance, placed).size()))
                    .orElseThrow();
            List<Candidate> candidates = validCandidates(next, placed);
            if (candidates.isEmpty()) {
                suggestions.addAll(conflictSuggestions(next, placed));
                return new SolverResult(List.copyOf(placed), suggestions, warnings);
            }

            Candidate best = candidates.stream()
                    .max(Comparator.comparingInt(Candidate::score))
                    .orElseThrow();
            placed.add(new ScheduleItem(Ids.next(), next.offeringId(), next.courseId(), next.sectionId(), next.labGroupId(),
                    best.room().id(), next.teacherId(), best.day(), best.slot(), next.kind(), best.warnings()));
            warnings.addAll(best.warnings());
            instances.remove(next);
        }

        List<String> finalClashes = studentClashes(placed);
        if (!finalClashes.isEmpty()) {
            return new SolverResult(List.copyOf(placed), finalClashes, warnings);
        }

        repo.schedule.clear();
        repo.schedule.addAll(placed);
        repo.lastSuggestions.clear();
        repo.lastWarnings.clear();
        repo.lastWarnings.addAll(warnings);
        return new SolverResult(List.copyOf(placed), List.of(), List.copyOf(warnings));
    }

    List<ClassInstance> buildClassInstances() {
        List<ClassInstance> instances = new ArrayList<>();
        for (CourseOffering offering : repo.offerings) {
            if (offering.lecturerId() == null) {
                continue;
            }
            Course course = repo.course(offering.courseId());
            List<Section> batchSections = repo.sections.stream()
                    .filter(section -> section.batchId().equals(offering.batchId()))
                    .sorted(Comparator.comparing(Section::name))
                    .toList();

            for (Section section : batchSections) {
                for (int i = 0; i < course.weeklyTheoryCount(); i++) {
                    instances.add(new ClassInstance(Ids.next(), offering.id(), course.id(), SessionKind.THEORY, section.id(),
                            null, offering.lecturerId(), section.size(), course.needsTv()));
                }
            }

            if (course.requiresLab() && offering.labInstructorId() != null) {
                for (Section section : batchSections) {
                    List<LabGroup> groups = repo.labGroups.stream()
                            .filter(group -> group.sectionId().equals(section.id()))
                            .sorted(Comparator.comparing(LabGroup::name))
                            .toList();
                    for (LabGroup group : groups) {
                        for (int i = 0; i < course.weeklyLabCount(); i++) {
                            instances.add(new ClassInstance(Ids.next(), offering.id(), course.id(), SessionKind.LAB, section.id(),
                                    group.id(), offering.labInstructorId(), group.size(), false));
                        }
                    }
                }
            }
        }
        return instances;
    }

    private List<Candidate> validCandidates(ClassInstance instance, List<ScheduleItem> placed) {
        List<Candidate> candidates = new ArrayList<>();
        Teacher teacher = repo.teacher(instance.teacherId());

        for (DayOfWeek day : Week.TEACHING_DAYS) {
            for (TimeSlot slot : TimeSlot.grid()) {
                if (!teacher.isAvailable(day, slot)) {
                    continue;
                }
                if (dailyLoad(placed, instance, day) >= 3) {
                    continue;
                }
                if (placed.stream().anyMatch(item -> item.day() == day && item.slot().id().equals(slot.id())
                        && (item.teacherId().equals(instance.teacherId()) || sameStudentEntity(item, instance)))) {
                    continue;
                }

                for (Room room : repo.rooms) {
                    if (!roomFits(room, instance)) {
                        continue;
                    }
                    if (placed.stream().anyMatch(item -> item.day() == day && item.slot().id().equals(slot.id()) && item.roomId().equals(room.id()))) {
                        continue;
                    }
                    Candidate candidate = score(day, slot, room, instance);
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private boolean roomFits(Room room, ClassInstance instance) {
        if (room.capacity() < instance.size()) {
            return false;
        }
        if (instance.kind() == SessionKind.LAB) {
            return room.type() == RoomType.COMPUTER_LAB;
        }
        return room.type() == RoomType.LECTURE_ROOM;
    }

    private Candidate score(DayOfWeek day, TimeSlot slot, Room room, ClassInstance instance) {
        int score = 100;
        List<String> warnings = new ArrayList<>();

        if (instance.needsTv() && room.equipment() == Equipment.TV_BOARD) {
            score += 20;
        } else if (instance.needsTv()) {
            score -= 15;
            warnings.add("Soft warning: " + repo.course(instance.courseId()).name()
                    + " needs TV, but " + room.name() + " is BoardOnly.");
        }

        return new Candidate(day, slot, room, score, warnings);
    }

    private List<String> conflictSuggestions(ClassInstance instance, List<ScheduleItem> placed) {
        List<String> suggestions = new ArrayList<>();
        Course course = repo.course(instance.courseId());
        String audience = instance.kind() == SessionKind.THEORY
                ? repo.section(instance.sectionId()).name()
                : repo.labGroup(instance.labGroupId()).name();

        placed.stream()
                .filter(item -> item.teacherId().equals(instance.teacherId()))
                .limit(2)
                .forEach(item -> suggestions.add("Move " + repo.course(item.courseId()).name() + " from "
                        + item.day() + " " + item.slot().id() + " so " + course.name()
                        + " can use teacher " + repo.teacher(instance.teacherId()).name() + "."));

        repo.rooms.stream()
                .filter(room -> room.capacity() >= instance.size())
                .filter(room -> instance.kind() == SessionKind.LAB ? room.type() == RoomType.COMPUTER_LAB : room.type() == RoomType.LECTURE_ROOM)
                .limit(2)
                .forEach(room -> suggestions.add("Try assigning " + course.name() + " for " + audience
                        + " to " + room.name() + " in the earliest free slot."));

        suggestions.add("Reduce the same-day load for " + audience + " by moving one class to another teaching day.");
        suggestions.add("Assign an alternate " + (instance.kind() == SessionKind.LAB ? "lab instructor" : "lecturer")
                + " with availability in the remaining free slots.");

        return suggestions.stream().distinct().limit(5).toList();
    }

    boolean canPlace(ScheduleItem moving, DayOfWeek day, TimeSlot slot, Room room, List<ScheduleItem> basis) {
        if (room.capacity() < audienceSize(moving)) {
            return false;
        }
        Teacher teacher = repo.teacher(moving.teacherId());
        if (!teacher.isAvailable(day, slot)) {
            return false;
        }
        ClassInstance instance = new ClassInstance(Ids.next(), moving.offeringId(), moving.courseId(), moving.kind(),
                moving.sectionId(), moving.labGroupId(), moving.teacherId(), audienceSize(moving), repo.course(moving.courseId()).needsTv());
        List<ScheduleItem> others = basis.stream().filter(item -> !item.id().equals(moving.id())).toList();
        if (dailyLoad(others, instance, day) >= 3) {
            return false;
        }
        return others.stream().noneMatch(item -> item.day() == day && item.slot().id().equals(slot.id())
                && (item.roomId().equals(room.id()) || item.teacherId().equals(moving.teacherId()) || sameStudentEntity(item, instance)));
    }

    private int dailyLoad(List<ScheduleItem> items, ClassInstance instance, DayOfWeek day) {
        if (instance.kind() == SessionKind.LAB) {
            int load = 0;
            for (ScheduleItem item : items) {
                if (item.day() != day || item.kind() == SessionKind.EXAM) {
                    continue;
                }
                boolean sameSectionTheory = item.kind() == SessionKind.THEORY
                        && Objects.equals(sectionId(item), sectionId(instance));
                boolean sameLabGroup = item.kind() == SessionKind.LAB
                        && Objects.equals(item.labGroupId(), instance.labGroupId());
                if (sameSectionTheory || sameLabGroup) {
                    load++;
                }
            }
            return load;
        }

        String sectionId = sectionId(instance);
        List<LabGroup> groups = repo.labGroups.stream()
                .filter(group -> group.sectionId().equals(sectionId))
                .toList();
        if (groups.isEmpty()) {
            return sectionTheoryLoad(items, sectionId, day);
        }

        int maxLoad = 0;
        for (LabGroup group : groups) {
            int load = sectionTheoryLoad(items, sectionId, day);
            for (ScheduleItem item : items) {
                if (item.day() == day
                        && item.kind() == SessionKind.LAB
                        && Objects.equals(item.labGroupId(), group.id())) {
                    load++;
                }
            }
            if (load > maxLoad) {
                maxLoad = load;
            }
        }
        return maxLoad;
    }

    private int sectionTheoryLoad(List<ScheduleItem> items, String sectionId, DayOfWeek day) {
        int load = 0;
        for (ScheduleItem item : items) {
            if (item.day() == day
                    && item.kind() == SessionKind.THEORY
                    && Objects.equals(sectionId(item), sectionId)) {
                load++;
            }
        }
        return load;
    }

    private boolean sameStudentEntity(ScheduleItem item, ClassInstance instance) {
        if (item.kind() == SessionKind.EXAM) {
            return false;
        }
        String itemSectionId = sectionId(item);
        String instanceSectionId = sectionId(instance);
        if (item.kind() == SessionKind.THEORY || instance.kind() == SessionKind.THEORY) {
            return itemSectionId != null && itemSectionId.equals(instanceSectionId);
        }
        return Objects.equals(item.labGroupId(), instance.labGroupId());
    }

    private boolean sameStudentEntity(ScheduleItem left, ScheduleItem right) {
        if (left.kind() == SessionKind.EXAM || right.kind() == SessionKind.EXAM) {
            return false;
        }
        String leftSectionId = sectionId(left);
        String rightSectionId = sectionId(right);
        if (left.kind() == SessionKind.THEORY || right.kind() == SessionKind.THEORY) {
            return leftSectionId != null && leftSectionId.equals(rightSectionId);
        }
        return Objects.equals(left.labGroupId(), right.labGroupId());
    }

    private String sectionId(ScheduleItem item) {
        if (item.sectionId() != null) {
            return item.sectionId();
        }
        if (item.labGroupId() != null) {
            return repo.labGroup(item.labGroupId()).sectionId();
        }
        return null;
    }

    private String sectionId(ClassInstance instance) {
        if (instance.sectionId() != null) {
            return instance.sectionId();
        }
        if (instance.labGroupId() != null) {
            return repo.labGroup(instance.labGroupId()).sectionId();
        }
        return null;
    }

    private List<String> studentClashes(List<ScheduleItem> items) {
        List<String> clashes = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                ScheduleItem left = items.get(i);
                ScheduleItem right = items.get(j);
                if (left.day() == right.day()
                        && left.slot().id().equals(right.slot().id())
                        && sameStudentEntity(left, right)) {
                    clashes.add("Conflict: " + repo.course(left.courseId()).name()
                            + " and " + repo.course(right.courseId()).name()
                            + " overlap for " + repo.audience(left)
                            + " on " + left.day() + " " + left.slot().id() + ".");
                }
            }
        }
        return clashes;
    }

    int audienceSize(ScheduleItem item) {
        if (item.kind() == SessionKind.THEORY) {
            return repo.section(item.sectionId()).size();
        }
        if (item.kind() == SessionKind.LAB) {
            return repo.labGroup(item.labGroupId()).size();
        }
        Exam exam = repo.exams.stream().filter(candidate -> candidate.id().equals(item.offeringId())).findFirst().orElseThrow();
        return repo.batches.stream()
                .filter(batch -> batch.programId().equals(exam.programId()))
                .mapToInt(Batch::studentCount)
                .sum();
    }
}

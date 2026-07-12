package edu.hilcoe.acse;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

final class ScheduleEngine {
    private static final int MAX_SEARCH_NODES = 50_000;

    private final AcseRepository repo;
    private int searchNodes;
    private boolean searchLimitHit;

    ScheduleEngine(AcseRepository repo) {
        this.repo = repo;
    }

    SolverResult generate() {
        searchNodes = 0;
        searchLimitHit = false;
        List<ClassInstance> instances = buildClassInstances();
        List<ScheduleItem> placed = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        placed = randomizedGreedyPlacement(instances);
        if (placed.size() == instances.size()) {
            placed.forEach(item -> warnings.addAll(item.warnings()));
            warnings.addAll(ScheduleRules.teacherBlockWarnings(placed));
            repo.schedule.clear();
            repo.schedule.addAll(placed);
            repo.lastSuggestions.clear();
            repo.lastWarnings.clear();
            repo.lastWarnings.addAll(warnings);
            return new SolverResult(List.copyOf(placed), List.of(), List.copyOf(warnings));
        }
        placed = new ArrayList<>();

        if (!placeAll(instances, placed)) {
            List<ScheduleItem> greedyPlaced = greedyPlacement(instances);
            if (greedyPlaced.size() == instances.size()) {
                greedyPlaced.forEach(item -> warnings.addAll(item.warnings()));
                warnings.addAll(ScheduleRules.teacherBlockWarnings(greedyPlaced));
                repo.schedule.clear();
                repo.schedule.addAll(greedyPlaced);
                repo.lastSuggestions.clear();
                repo.lastWarnings.clear();
                repo.lastWarnings.addAll(warnings);
                return new SolverResult(List.copyOf(greedyPlaced), List.of(), List.copyOf(warnings));
            }
            if (greedyPlaced.size() > placed.size()) {
                placed = greedyPlaced;
            }
            List<ScheduleItem> partialPlacement = placed;
            ClassInstance blocked = instances.stream()
                    .min(Comparator.comparingInt(instance -> validCandidates(instance, partialPlacement).size()))
                    .orElse(null);
            if (blocked != null) {
                suggestions.addAll(conflictSuggestions(blocked, placed));
            }
            if (searchLimitHit) {
                suggestions.add("The scheduler tried many possible placements but could not finish. Relax one constraint, add more available days, or split the setup into fewer course offerings.");
            }
            return new SolverResult(List.copyOf(placed), suggestions, warnings);
        }

        placed.forEach(item -> warnings.addAll(item.warnings()));
        warnings.addAll(ScheduleRules.teacherBlockWarnings(placed));
        repo.schedule.clear();
        repo.schedule.addAll(placed);
        repo.lastSuggestions.clear();
        repo.lastWarnings.clear();
        repo.lastWarnings.addAll(warnings);
        return new SolverResult(List.copyOf(placed), List.of(), List.copyOf(warnings));
    }

    private List<ScheduleItem> randomizedGreedyPlacement(List<ClassInstance> instances) {
        List<ScheduleItem> best = List.of();
        for (int attempt = 0; attempt < 800; attempt++) {
            Random random = new Random(attempt);
            List<ClassInstance> remaining = new ArrayList<>(instances);
            List<ScheduleItem> placed = new ArrayList<>();
            while (!remaining.isEmpty()) {
                int smallestDomain = Integer.MAX_VALUE;
                List<ClassInstance> tied = new ArrayList<>();
                for (ClassInstance instance : remaining) {
                    int size = validCandidates(instance, placed).size();
                    if (size < smallestDomain) {
                        smallestDomain = size;
                        tied.clear();
                        tied.add(instance);
                    } else if (size == smallestDomain) {
                        tied.add(instance);
                    }
                }
                if (smallestDomain == 0) {
                    break;
                }
                ClassInstance next = tied.get(random.nextInt(tied.size()));
                List<Candidate> candidates = validCandidates(next, placed).stream()
                        .sorted(Comparator.comparingInt(Candidate::score).reversed())
                        .toList();
                int choiceLimit = Math.min(5, candidates.size());
                Candidate selected = candidates.get(random.nextInt(choiceLimit));
                placed.add(new ScheduleItem(Ids.next(), next.offeringId(), next.courseId(), next.sectionId(), next.labGroupId(),
                        selected.room().id(), next.teacherId(), selected.day(), selected.slot(), next.kind(), selected.warnings()));
                remaining.remove(next);
            }
            if (placed.size() > best.size()) {
                best = placed;
            }
            if (placed.size() == instances.size()) {
                return placed;
            }
        }
        return best;
    }

    private List<ScheduleItem> greedyPlacement(List<ClassInstance> instances) {
        List<ClassInstance> remaining = new ArrayList<>(instances);
        List<ScheduleItem> placed = new ArrayList<>();
        while (!remaining.isEmpty()) {
            ClassInstance next = remaining.stream()
                    .min(Comparator.comparingInt(instance -> validCandidates(instance, placed).size()))
                    .orElseThrow();
            List<Candidate> candidates = validCandidates(next, placed);
            if (candidates.isEmpty()) {
                return placed;
            }
            Candidate best = candidates.stream()
                    .max(Comparator.comparingInt(Candidate::score))
                    .orElseThrow();
            placed.add(new ScheduleItem(Ids.next(), next.offeringId(), next.courseId(), next.sectionId(), next.labGroupId(),
                    best.room().id(), next.teacherId(), best.day(), best.slot(), next.kind(), best.warnings()));
            remaining.remove(next);
        }
        return placed;
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

    private boolean placeAll(List<ClassInstance> remaining, List<ScheduleItem> placed) {
        if (++searchNodes > MAX_SEARCH_NODES) {
            searchLimitHit = true;
            return false;
        }
        if (remaining.isEmpty()) {
            return true;
        }

        ClassInstance next = remaining.stream()
                .min(Comparator.comparingInt(instance -> validCandidates(instance, placed).size()))
                .orElseThrow();
        List<Candidate> candidates = validCandidates(next, placed).stream()
                .sorted(Comparator.comparingInt(Candidate::score).reversed())
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }

        List<ClassInstance> nextRemaining = new ArrayList<>(remaining);
        nextRemaining.remove(next);
        for (Candidate candidate : candidates) {
            ScheduleItem item = new ScheduleItem(Ids.next(), next.offeringId(), next.courseId(), next.sectionId(), next.labGroupId(),
                    candidate.room().id(), next.teacherId(), candidate.day(), candidate.slot(), next.kind(), candidate.warnings());
            placed.add(item);
            if (placeAll(nextRemaining, placed)) {
                return true;
            }
            placed.remove(placed.size() - 1);
        }
        return false;
    }

    private List<Candidate> validCandidates(ClassInstance instance, List<ScheduleItem> placed) {
        List<Candidate> candidates = new ArrayList<>();
        Teacher teacher = repo.teacher(instance.teacherId());

        for (DayOfWeek day : Week.TEACHING_DAYS) {
            for (TimeSlot slot : TimeSlot.grid()) {
                if (!teacher.isAvailable(day, slot)) {
                    continue;
                }
                if (ScheduleRules.dailyLoad(placed, instance, day) >= 3) {
                    continue;
                }
                if (hasSameTheoryCourseOnDay(placed, instance, day)) {
                    continue;
                }
                if (placed.stream().anyMatch(item -> item.day() == day && item.slot().id().equals(slot.id())
                        && (item.teacherId().equals(instance.teacherId()) || ScheduleRules.sameEntity(item, instance)))) {
                    continue;
                }

                for (Room room : repo.rooms) {
                    if (!roomFits(room, instance)) {
                        continue;
                    }
                    if (placed.stream().anyMatch(item -> item.day() == day && item.slot().id().equals(slot.id()) && item.roomId().equals(room.id()))) {
                        continue;
                    }
                    Candidate candidate = score(day, slot, room, instance, placed);
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private boolean hasSameTheoryCourseOnDay(List<ScheduleItem> placed, ClassInstance instance, DayOfWeek day) {
        if (instance.kind() != SessionKind.THEORY) {
            return false;
        }
        return placed.stream()
                .filter(item -> item.day() == day)
                .filter(item -> item.kind() == SessionKind.THEORY)
                .anyMatch(item -> item.courseId().equals(instance.courseId())
                        && item.sectionId().equals(instance.sectionId()));
    }

    private boolean hasBackToBackStudentSession(List<ScheduleItem> placed, ClassInstance instance, DayOfWeek day, TimeSlot slot) {
        return placed.stream()
                .filter(item -> item.day() == day)
                .filter(item -> ScheduleRules.sameEntity(item, instance))
                .anyMatch(item -> Math.abs(slotIndex(item.slot()) - slotIndex(slot)) == 1);
    }

    private int slotIndex(TimeSlot slot) {
        List<TimeSlot> grid = TimeSlot.grid();
        for (int i = 0; i < grid.size(); i++) {
            if (grid.get(i).id().equals(slot.id())) {
                return i;
            }
        }
        return -10;
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

    private Candidate score(DayOfWeek day, TimeSlot slot, Room room, ClassInstance instance, List<ScheduleItem> placed) {
        int score = 100;
        List<String> warnings = new ArrayList<>();

        Set<Block> existingBlocks = new HashSet<>();
        for (ScheduleItem item : placed) {
            if (item.teacherId().equals(instance.teacherId()) && item.day() == day) {
                existingBlocks.add(item.slot().block());
            }
        }
        if (!existingBlocks.isEmpty() && !existingBlocks.contains(slot.block())) {
            score -= 35;
            warnings.add("Soft warning: teacher " + repo.teacher(instance.teacherId()).name()
                    + " has a mixed daily block on " + day + ".");
        }

        if (instance.needsTv() && room.equipment() == Equipment.TV_BOARD) {
            score += 20;
        } else if (instance.needsTv()) {
            score -= 15;
            warnings.add("Soft warning: " + repo.course(instance.courseId()).name()
                    + " needs TV, but " + room.name() + " is BoardOnly.");
        }

        if (hasBackToBackStudentSession(placed, instance, day, slot)) {
            score -= 25;
        }

        if (slot.block() == Block.MORNING) {
            score += 5;
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
        if (ScheduleRules.dailyLoad(others, instance, day) >= 3) {
            return false;
        }
        if (hasSameTheoryCourseOnDay(others, instance, day)) {
            return false;
        }
        return others.stream().noneMatch(item -> item.day() == day && item.slot().id().equals(slot.id())
                && (item.roomId().equals(room.id()) || item.teacherId().equals(moving.teacherId()) || ScheduleRules.sameEntity(item, instance)));
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

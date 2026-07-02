package edu.hilcoe.acse;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class AcseApplication extends Application {
    private final AcseRepository repo = SeedData.create();
    private final ScheduleEngine scheduler = new ScheduleEngine(repo);
    private final ExamService exams = new ExamService(repo, scheduler);
    private final BorderPane root = new BorderPane();
    private List<String> dashboardMessages = List.of();
    private String selectedFilterType = "All";
    private String selectedFilterValue = "All";
    private String selectedSectionFilter = "All Sections";
    private String setupAcademicYear = "2026/27";
    private SemesterPeriod setupSemester = SemesterPeriod.FIRST;
    private User currentUser;

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(root, 1240, 780);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        showLogin();
        stage.setTitle("Adaptive College Scheduling Engine");
        stage.setMinWidth(1040);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }

    private void showLogin() {
        VBox card = new VBox(14);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(380);
        Label title = new Label("Adaptive College Scheduling Engine");
        title.getStyleClass().add("login-title");
        TextField email = new TextField("admin@acse.local");
        email.setPromptText("Email");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label error = new Label();
        error.getStyleClass().add("error");
        Button login = new Button("Sign in");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setOnAction(event -> repo.findUserByEmail(email.getText()).ifPresentOrElse(user -> {
            currentUser = user;
            showShell();
        }, () -> error.setText("Unknown demo account.")));
        card.getChildren().addAll(title, new Label("Use admin@acse.local or student@acse.local"), email, password, login, error);
        root.setCenter(new StackPane(card));
    }

    private void showShell() {
        root.setLeft(sidebar());
        switch (currentUser.role()) {
            case ADMIN -> showAdmin();
            case TEACHER -> showTeacher();
            case STUDENT -> showStudent();
        }
    }

    private VBox sidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");
        Label product = new Label("ACSE");
        product.getStyleClass().add("brand");
        Label who = new Label(currentUser.name() + "\n" + currentUser.role());
        who.getStyleClass().add("who");
        Button dashboard = navButton("Dashboard");
        dashboard.setOnAction(event -> showShell());
        Button logout = navButton("Logout");
        logout.setOnAction(event -> {
            currentUser = null;
            root.setLeft(null);
            showLogin();
        });
        side.getChildren().addAll(product, who, dashboard, new Separator());
        if (currentUser.role() == UserRole.ADMIN) {
            side.getChildren().add(occupancyPanel());
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        side.getChildren().addAll(spacer, logout);
        return side;
    }

    private Button navButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private VBox occupancyPanel() {
        VBox box = new VBox(8);
        Label label = new Label("Room Occupancy");
        label.getStyleClass().add("section-label");
        box.getChildren().add(label);
        Map<String, Integer> occupancy = repo.roomOccupancy();
        occupancy.forEach((room, count) -> {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Circle dot = new Circle(5, count > 0 ? Color.web("#16a34a") : Color.web("#94a3b8"));
            Label text = new Label(room + "  " + count + " bookings");
            row.getChildren().addAll(dot, text);
            box.getChildren().add(row);
        });
        return box;
    }

    private void showAdmin() {
        VBox page = new VBox(14);
        page.getStyleClass().add("page");
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button generate = new Button("Generate Schedule");
        generate.getStyleClass().add("primary-button");
        Button exam = new Button("Apply Exam Override");
        Button clear = new Button("Clear Schedule");
        ComboBox<String> filterType = new ComboBox<>();
        filterType.getItems().addAll("All", "Teacher", "Batch", "Course");
        filterType.setValue(selectedFilterType);
        ComboBox<String> filterValue = new ComboBox<>();
        populateFilterValues(filterType.getValue(), filterValue);
        filterValue.setValue(selectedFilterValue);
        ComboBox<String> sectionFilter = new ComboBox<>();
        populateSectionFilter(sectionFilter);
        filterType.setOnAction(event -> {
            selectedFilterType = filterType.getValue();
            selectedFilterValue = "All";
            selectedSectionFilter = "All Sections";
            populateFilterValues(selectedFilterType, filterValue);
            populateSectionFilter(sectionFilter);
        });
        filterValue.setOnAction(event -> {
            selectedFilterValue = filterValue.getValue() == null ? "All" : filterValue.getValue();
            selectedSectionFilter = "All Sections";
            populateSectionFilter(sectionFilter);
        });
        Button applyFilter = new Button("Apply Filter");
        applyFilter.setOnAction(event -> {
            selectedFilterType = filterType.getValue();
            selectedFilterValue = filterValue.getValue() == null ? "All" : filterValue.getValue();
            selectedSectionFilter = sectionFilter.getValue() == null ? "All Sections" : sectionFilter.getValue();
            showAdmin();
        });
        toolbar.getChildren().addAll(new Label("Admin Dashboard"), generate, clear, exam, filterType, filterValue, sectionFilter, applyFilter);

        ListView<String> warnings = new ListView<>();
        warnings.setPrefHeight(130);
        warnings.getStyleClass().add("warnings");
        warnings.getItems().setAll(dashboardMessages);
        generate.setOnAction(event -> {
            List<String> unassigned = repo.offeringsMissingTeachers().stream()
                    .map(offering -> repo.course(offering.courseId()).code() + " (" + repo.batch(offering.batchId()).name() + ")")
                    .toList();
            if (!unassigned.isEmpty()) {
                dashboardMessages = List.of("Assign teachers to all course offerings before generating: "
                        + String.join(", ", unassigned));
                showAdmin();
                return;
            }
            if (repo.offerings.isEmpty()) {
                dashboardMessages = List.of("Add batches and course offerings in Academic Setup first.");
                showAdmin();
                return;
            }
            SolverResult result = scheduler.generate();
            dashboardMessages = result.success()
                    ? result.warnings()
                    : result.suggestions().stream().map(suggestion -> "Conflict: " + suggestion).toList();
            showAdmin();
        });
        exam.setOnAction(event -> {
            if (repo.offerings.isEmpty()) {
                dashboardMessages = List.of("Add course offerings in Academic Setup before applying an exam override.");
                showAdmin();
                return;
            }
            if (repo.schedule.isEmpty()) {
                scheduler.generate();
            }
            Course course = repo.courses.getFirst();
            CourseOffering offering = repo.offerings.getFirst();
            Program program = repo.program(repo.batch(offering.batchId()).programId());
            dashboardMessages = exams.scheduleExam(course.id(), program.id(), DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(11, 0));
            showAdmin();
        });
        clear.setOnAction(event -> {
            repo.schedule.clear();
            repo.lastSuggestions.clear();
            repo.lastWarnings.clear();
            dashboardMessages = List.of("Generated schedule cleared. Master data and assignments were kept.");
            showAdmin();
        });

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("admin-tabs");
        List<ScheduleItem> visibleSchedule = filteredSchedule();
        tabs.getTabs().add(tab("Schedule", new VBox(12, weekView(repo.payloadFor(visibleSchedule)), detailsTable(visibleSchedule),
                new Label("Warnings and Suggestions"), warnings)));
        tabs.getTabs().add(tab("Conflicts", conflictPanel()));
        tabs.getTabs().add(tab("Academic Setup", academicSetupPanel()));
        tabs.getTabs().add(tab("Teacher Assignments", teacherAssignmentPanel()));
        page.getChildren().addAll(toolbar, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.setCenter(page);
        root.setLeft(sidebar());
    }

    private Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private VBox conflictPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("setup-panel");
        Label title = new Label("Conflict Management");
        title.getStyleClass().add("panel-title");
        Label policy = new Label("Hard conflicts are never auto-forced. Adjust teacher availability in Teacher Assignments, course offerings, or batch setup, then generate again.");
        policy.getStyleClass().add("muted");
        policy.setWrapText(true);
        ListView<String> suggestions = new ListView<>();
        suggestions.setPrefHeight(220);
        suggestions.getItems().setAll(dashboardMessages.isEmpty()
                ? List.of("No conflicts yet. Generate a schedule to validate the current setup.")
                : dashboardMessages);
        panel.getChildren().addAll(title, policy, suggestions);
        return panel;
    }

    private void populateFilterValues(String filterType, ComboBox<String> filterValue) {
        filterValue.getItems().clear();
        filterValue.getItems().add("All");
        switch (filterType) {
            case "Teacher" -> repo.teachers.stream()
                    .map(Teacher::name)
                    .sorted()
                    .forEach(filterValue.getItems()::add);
            case "Batch" -> repo.batches.stream()
                    .map(Batch::name)
                    .sorted()
                    .forEach(filterValue.getItems()::add);
            case "Course" -> repo.courses.stream()
                    .map(course -> course.code() + " - " + course.name())
                    .sorted()
                    .forEach(filterValue.getItems()::add);
            default -> {
            }
        }
        if (!filterValue.getItems().contains(selectedFilterValue)) {
            selectedFilterValue = "All";
        }
        filterValue.setValue(selectedFilterValue);
    }

    private void populateSectionFilter(ComboBox<String> sectionFilter) {
        sectionFilter.getItems().clear();
        sectionFilter.getItems().add("All Sections");
        boolean enabled = selectedFilterType.equals("Batch") && !selectedFilterValue.equals("All");
        if (enabled) {
            repo.batches.stream()
                    .filter(batch -> batch.name().equals(selectedFilterValue))
                    .findFirst()
                    .ifPresent(batch -> repo.sections.stream()
                            .filter(section -> section.batchId().equals(batch.id()))
                            .map(Section::name)
                            .sorted()
                            .forEach(sectionFilter.getItems()::add));
        }
        if (!sectionFilter.getItems().contains(selectedSectionFilter)) {
            selectedSectionFilter = "All Sections";
        }
        sectionFilter.setValue(selectedSectionFilter);
        sectionFilter.setDisable(!enabled);
    }

    private List<ScheduleItem> filteredSchedule() {
        if (selectedFilterType.equals("All") || selectedFilterValue.equals("All")) {
            return List.copyOf(repo.schedule);
        }
        return repo.schedule.stream()
                .filter(item -> switch (selectedFilterType) {
                    case "Teacher" -> item.kind() != SessionKind.EXAM && repo.teacher(item.teacherId()).name().equals(selectedFilterValue);
                    case "Batch" -> item.kind() != SessionKind.EXAM
                            && repo.batch(repo.offering(item.offeringId()).batchId()).name().equals(selectedFilterValue)
                            && matchesSelectedSection(item);
                    case "Course" -> {
                        Course course = repo.course(item.courseId());
                        yield (course.code() + " - " + course.name()).equals(selectedFilterValue);
                    }
                    default -> true;
                })
                .toList();
    }

    private boolean matchesSelectedSection(ScheduleItem item) {
        if (selectedSectionFilter.equals("All Sections")) {
            return true;
        }
        if (item.kind() == SessionKind.THEORY && item.sectionId() != null) {
            return repo.section(item.sectionId()).name().equals(selectedSectionFilter);
        }
        if (item.kind() == SessionKind.LAB && item.labGroupId() != null) {
            LabGroup group = repo.labGroup(item.labGroupId());
            return repo.section(group.sectionId()).name().equals(selectedSectionFilter);
        }
        return false;
    }

    private VBox academicSetupPanel() {
        VBox panel = new VBox(14);
        panel.getStyleClass().add("setup-panel");

        Label title = new Label("Academic Setup");
        title.getStyleClass().add("panel-title");
        Label intro = new Label("Step 1: Set the academic year and semester. Step 2: Add batches. Step 3: Add course offerings for each batch.");
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);

        TitledPane periodPane = new TitledPane("1. Academic Year & Semester", academicPeriodSection());
        periodPane.setExpanded(true);
        TitledPane batchPane = new TitledPane("2. Batches", batchSection());
        batchPane.setExpanded(true);
        TitledPane offeringPane = new TitledPane("3. Course Offerings", courseOfferingSection());
        offeringPane.setExpanded(true);

        panel.getChildren().addAll(title, intro, periodPane, batchPane, offeringPane);
        return panel;
    }

    private VBox academicPeriodSection() {
        VBox box = new VBox(8);
        TextField yearField = new TextField(setupAcademicYear);
        yearField.setPromptText("Academic year, e.g. 2026/27");
        ComboBox<SemesterPeriod> semesterBox = new ComboBox<>();
        semesterBox.getItems().addAll(SemesterPeriod.values());
        semesterBox.setValue(setupSemester);
        semesterBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(SemesterPeriod value) {
                return value == null ? "" : value == SemesterPeriod.FIRST ? "First Semester" : "Second Semester";
            }

            @Override
            public SemesterPeriod fromString(String value) {
                return "Second Semester".equals(value) ? SemesterPeriod.SECOND : SemesterPeriod.FIRST;
            }
        });
        Label status = new Label();
        status.getStyleClass().add("muted");
        Button apply = new Button("Set Active Period");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(event -> {
            if (yearField.getText().isBlank()) {
                status.setText("Enter an academic year.");
                return;
            }
            setupAcademicYear = yearField.getText().trim();
            setupSemester = semesterBox.getValue();
            Program program = repo.findOrCreateProgram(setupAcademicYear, setupSemester);
            status.setText("Active period: " + program.displayLabel());
        });
        Program current = repo.findOrCreateProgram(setupAcademicYear, setupSemester);
        status.setText("Active period: " + current.displayLabel());
        HBox fields = new HBox(8, new Label("Year"), yearField, new Label("Semester"), semesterBox, apply);
        fields.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(fields, status);
        return box;
    }

    private VBox batchSection() {
        VBox box = new VBox(8);
        Program activeProgram = repo.findOrCreateProgram(setupAcademicYear, setupSemester);
        TextField batchName = new TextField();
        batchName.setPromptText("Batch code, e.g. DRB2504");
        ComboBox<String> sections = new ComboBox<>();
        sections.getItems().addAll("No section", "Section A and B");
        sections.setValue("Section A and B");
        Label status = new Label();
        status.getStyleClass().add("muted");
        Button addBatch = new Button("Add Batch");
        addBatch.getStyleClass().add("primary-button");
        addBatch.setOnAction(event -> {
            if (batchName.getText().isBlank()) {
                status.setText("Enter a batch code first.");
                return;
            }
            Program program = repo.findOrCreateProgram(setupAcademicYear, setupSemester);
            List<String> sectionNames = sections.getValue().equals("No section")
                    ? List.of("Main")
                    : List.of("Section A", "Section B");
            Batch batch = SeedData.addBatch(repo, program, batchName.getText().trim().toUpperCase(), sectionNames);
            repo.schedule.clear();
            dashboardMessages = List.of(batch.name() + " added to " + program.displayLabel()
                    + " with " + String.join(", ", sectionNames) + ". Add course offerings next.");
            showAdmin();
        });
        HBox fields = new HBox(8, batchName, sections, addBatch);
        fields.setAlignment(Pos.CENTER_LEFT);
        TableView<Batch> batches = batchTable(activeProgram.id());
        batches.setMaxHeight(150);
        box.getChildren().addAll(fields, status, batches);
        return box;
    }

    private VBox courseOfferingSection() {
        VBox box = new VBox(8);
        Program program = repo.findOrCreateProgram(setupAcademicYear, setupSemester);
        ComboBox<Batch> batchBox = batchComboBox(repo.batchesForProgram(program.id()));
        TextField courseCode = new TextField();
        courseCode.setPromptText("Course code");
        TextField courseName = new TextField();
        courseName.setPromptText("Course name");
        CheckBox requiresLab = new CheckBox("Requires lab (2 lectures + 1 lab/week)");
        CheckBox needsTv = new CheckBox("Needs TV");
        Label loadHint = new Label("Without lab: 2 lecture classes/week. With lab: 2 lectures + 1 lab/week.");
        loadHint.getStyleClass().add("muted");
        loadHint.setWrapText(true);
        Label status = new Label();
        status.getStyleClass().add("muted");
        Button addOffering = new Button("Add Course Offering");
        addOffering.getStyleClass().add("primary-button");
        addOffering.setOnAction(event -> {
            if (batchBox.getValue() == null) {
                status.setText("Select a batch first.");
                return;
            }
            if (courseCode.getText().isBlank() || courseName.getText().isBlank()) {
                status.setText("Enter course code and name.");
                return;
            }
            Batch batch = batchBox.getValue();
            Course course = upsertCourse(courseCode.getText().trim().toUpperCase(), courseName.getText().trim(),
                    requiresLab.isSelected(), needsTv.isSelected());
            boolean exists = repo.offerings.stream()
                    .anyMatch(offering -> offering.batchId().equals(batch.id()) && offering.courseId().equals(course.id()));
            if (exists) {
                status.setText(course.code() + " is already offered to " + batch.name() + ".");
                return;
            }
            repo.offerings.add(new CourseOffering(Ids.next(), batch.id(), course.id(), null, null));
            repo.schedule.clear();
            dashboardMessages = List.of("Course offering added: " + course.code() + " for " + batch.name()
                    + ". Assign teachers in the Teacher Assignments tab.");
            showAdmin();
        });
        HBox fields = new HBox(8, batchBox, courseCode, courseName, requiresLab, needsTv, addOffering);
        fields.setAlignment(Pos.CENTER_LEFT);
        TableView<CourseOffering> offerings = courseOfferingTable(program.id());
        offerings.setMaxHeight(160);
        box.getChildren().addAll(fields, loadHint, status, offerings);
        return box;
    }

    private ComboBox<Batch> batchComboBox(List<Batch> batches) {
        ComboBox<Batch> batchBox = new ComboBox<>();
        batchBox.getItems().setAll(batches);
        batchBox.setConverter(batchConverter());
        if (!batches.isEmpty()) {
            batchBox.setValue(batches.getFirst());
        }
        return batchBox;
    }

    private javafx.util.StringConverter<Batch> batchConverter() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Batch value) {
                return value == null ? "" : value.name();
            }

            @Override
            public Batch fromString(String value) {
                return repo.batches.stream().filter(candidate -> candidate.name().equals(value)).findFirst().orElse(null);
            }
        };
    }

    private TableView<CourseOffering> courseOfferingTable(String programId) {
        TableView<CourseOffering> table = new TableView<>();
        table.getItems().setAll(repo.offerings.stream()
                .filter(offering -> repo.batch(offering.batchId()).programId().equals(programId))
                .toList());
        TableColumn<CourseOffering, String> batchCol = new TableColumn<>("Batch");
        batchCol.setCellValueFactory(data -> new SimpleStringProperty(repo.batch(data.getValue().batchId()).name()));
        TableColumn<CourseOffering, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(data -> {
            Course course = repo.course(data.getValue().courseId());
            return new SimpleStringProperty(course.code() + " - " + course.name());
        });
        TableColumn<CourseOffering, String> loadCol = new TableColumn<>("Weekly Load");
        loadCol.setCellValueFactory(data -> {
            Course course = repo.course(data.getValue().courseId());
            String load = course.requiresLab() ? "2 lectures + 1 lab" : "2 lectures";
            return new SimpleStringProperty(load);
        });
        TableColumn<CourseOffering, String> teacherCol = new TableColumn<>("Teachers");
        teacherCol.setCellValueFactory(data -> {
            CourseOffering offering = data.getValue();
            if (offering.lecturerId() == null) {
                return new SimpleStringProperty("Not assigned");
            }
            Course course = repo.course(offering.courseId());
            String lab = offering.labInstructorId() != null ? ", Lab: " + repo.teacher(offering.labInstructorId()).name() : "";
            return new SimpleStringProperty("Lecturer: " + repo.teacher(offering.lecturerId()).name() + lab);
        });
        table.getColumns().addAll(batchCol, courseCol, loadCol, teacherCol);
        return table;
    }

    private VBox teacherAssignmentPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("setup-panel");
        Label title = new Label("Teacher Assignment");
        title.getStyleClass().add("panel-title");
        Label intro = new Label("Select a batch, then choose a course offering for that batch. Assign teachers with per-day Morning, Afternoon, or Full day availability.");
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);

        Program program = repo.findOrCreateProgram(setupAcademicYear, setupSemester);
        ComboBox<Batch> batchBox = batchComboBox(repo.batchesForProgram(program.id()));
        ComboBox<CourseOffering> offeringBox = new ComboBox<>();
        offeringBox.setConverter(courseOfferingConverter());
        Runnable refreshOfferings = () -> {
            offeringBox.getItems().clear();
            if (batchBox.getValue() != null) {
                offeringBox.getItems().setAll(repo.offeringsForBatch(batchBox.getValue().id()));
                if (!offeringBox.getItems().isEmpty()) {
                    offeringBox.setValue(offeringBox.getItems().getFirst());
                }
            }
        };

        TextField lecturerName = new TextField();
        lecturerName.setPromptText("Lecturer name");
        TextField lecturerEmail = new TextField();
        lecturerEmail.setPromptText("Lecturer email");
        VBox lecturerAvailability = createDayAvailabilityEditor("Lecturer availability");

        TextField labName = new TextField();
        labName.setPromptText("Lab instructor name");
        TextField labEmail = new TextField();
        labEmail.setPromptText("Lab instructor email");
        VBox labAvailability = createDayAvailabilityEditor("Lab instructor availability");
        Label labRequirement = new Label();
        labRequirement.getStyleClass().add("muted");
        labRequirement.setWrapText(true);
        TitledPane labPane = new TitledPane("Lab Instructor", new VBox(8,
                labRequirement, new HBox(8, labName, labEmail), labAvailability));
        labPane.setExpanded(true);
        Runnable refreshLabPane = () -> {
            CourseOffering selected = offeringBox.getValue();
            boolean needsLabInstructor = selected != null && repo.course(selected.courseId()).requiresLab();
            labPane.setVisible(needsLabInstructor);
            labPane.setManaged(needsLabInstructor);
            if (needsLabInstructor) {
                Course course = repo.course(selected.courseId());
                labRequirement.setText(course.code() + " requires a lab session, so provide the lab instructor using the same availability procedure.");
            } else {
                labRequirement.setText("");
            }
        };
        offeringBox.setOnAction(event -> refreshLabPane.run());
        batchBox.setOnAction(event -> {
            refreshOfferings.run();
            refreshLabPane.run();
        });
        refreshOfferings.run();
        refreshLabPane.run();

        Label status = new Label();
        status.getStyleClass().add("muted");
        Button save = new Button("Save Teacher Assignment");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            if (batchBox.getValue() == null || offeringBox.getValue() == null) {
                status.setText("Select a batch and course offering first.");
                return;
            }
            if (lecturerName.getText().isBlank() || lecturerEmail.getText().isBlank()) {
                status.setText("Enter lecturer name and email.");
                return;
            }
            Map<DayOfWeek, Set<String>> lecturerSlots = readAvailability(lecturerAvailability);
            if (lecturerSlots.isEmpty()) {
                status.setText("Select at least one working day for the lecturer and set Morning or Afternoon for each day.");
                return;
            }

            CourseOffering offering = offeringBox.getValue();
            Course course = repo.course(offering.courseId());
            Teacher labInstructor = null;
            if (course.requiresLab()) {
                if (labName.getText().isBlank() || labEmail.getText().isBlank()) {
                    status.setText("Enter lab instructor name and email.");
                    return;
                }
                Map<DayOfWeek, Set<String>> labSlots = readAvailability(labAvailability);
                if (labSlots.isEmpty()) {
                    status.setText("Select at least one working day for the lab instructor with Morning or Afternoon per day.");
                    return;
                }
                User labUser = User.of(UserRole.TEACHER, labName.getText().trim(), labEmail.getText().trim());
                labInstructor = new Teacher(Ids.next(), labUser.id(), labUser.name(), TeacherRole.LAB_INSTRUCTOR, labSlots);
                repo.users.add(labUser);
                repo.teachers.add(labInstructor);
            }

            User lecturerUser = User.of(UserRole.TEACHER, lecturerName.getText().trim(), lecturerEmail.getText().trim());
            Teacher lecturer = new Teacher(Ids.next(), lecturerUser.id(), lecturerUser.name(), TeacherRole.LECTURER, lecturerSlots);
            repo.users.add(lecturerUser);
            repo.teachers.add(lecturer);

            assignTeachersToOffering(offering, lecturer, labInstructor);
            repo.schedule.clear();
            String labNote = course.requiresLab() && labInstructor != null
                    ? " Lab instructor also applied to matching " + course.code() + " offerings."
                    : "";
            dashboardMessages = List.of(lecturer.name() + " assigned to " + course.code() + " for "
                    + batchBox.getValue().name() + "." + labNote + " Generate the schedule again to apply it.");
            showAdmin();
        });

        HBox batchRow = new HBox(8, new Label("Batch"), batchBox, new Label("Course"), offeringBox);
        batchRow.setAlignment(Pos.CENTER_LEFT);
        HBox lecturerRow = new HBox(8, lecturerName, lecturerEmail);
        lecturerRow.setAlignment(Pos.CENTER_LEFT);
        TableView<Teacher> teachers = teacherTable();
        teachers.setMaxHeight(130);
        panel.getChildren().addAll(title, intro, batchRow, new Label("Lecturer"), lecturerRow, lecturerAvailability,
                labPane, save, status, teachers);
        return panel;
    }

    private VBox createDayAvailabilityEditor(String heading) {
        VBox box = new VBox(6);
        Label header = new Label(heading + " — choose Morning, Afternoon, or Full day independently for each day:");
        header.getStyleClass().add("muted");
        header.setWrapText(true);
        box.getChildren().add(header);
        List<DayAvailabilityControls> controls = new ArrayList<>();
        for (DayOfWeek day : Week.TEACHING_DAYS) {
            DayAvailabilityControls control = new DayAvailabilityControls(day);
            controls.add(control);
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(control.enabled, new Label("Shift:"), control.shift);
            box.getChildren().add(row);
        }
        box.setUserData(controls);
        return box;
    }

    private Map<DayOfWeek, Set<String>> readAvailability(VBox editor) {
        @SuppressWarnings("unchecked")
        List<DayAvailabilityControls> controls = (List<DayAvailabilityControls>) editor.getUserData();
        Map<DayOfWeek, Set<String>> availability = new EnumMap<>(DayOfWeek.class);
        for (DayAvailabilityControls control : controls) {
            if (control.enabled.isSelected()) {
                Set<String> slots = switch (control.shift.getValue()) {
                    case "Afternoon" -> Set.of("A1", "A2");
                    case "Full day" -> Set.of("M1", "M2", "M3", "A1", "A2");
                    default -> Set.of("M1", "M2", "M3");
                };
                availability.put(control.day, slots);
            }
        }
        return availability;
    }

    private final class DayAvailabilityControls {
        final DayOfWeek day;
        final CheckBox enabled;
        final ComboBox<String> shift;

        DayAvailabilityControls(DayOfWeek day) {
            this.day = day;
            enabled = new CheckBox(labelDay(day));
            shift = new ComboBox<>();
            shift.getItems().addAll("Morning", "Afternoon", "Full day");
            shift.setValue("Morning");
            shift.setDisable(true);
            enabled.selectedProperty().addListener((obs, oldValue, selected) -> shift.setDisable(!selected));
        }
    }

    private Course upsertCourse(String code, String name, boolean requiresLab, boolean needsTv) {
        Course existing = repo.courses.stream()
                .filter(course -> course.code().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
        Course course = Course.create(existing == null ? Ids.next() : existing.id(), code, name, requiresLab, needsTv);
        if (existing != null) {
            repo.courses.remove(existing);
        }
        repo.courses.add(course);
        return course;
    }

    private void assignTeachersToOffering(CourseOffering offering, Teacher lecturer, Teacher labInstructor) {
        Course course = repo.course(offering.courseId());
        String labInstructorId = course.requiresLab() && labInstructor != null ? labInstructor.id() : null;
        repo.offerings.remove(offering);
        repo.offerings.add(new CourseOffering(offering.id(), offering.batchId(), offering.courseId(), lecturer.id(), labInstructorId));
        if (labInstructorId != null) {
            List<CourseOffering> sameCourseLabOfferings = repo.offerings.stream()
                    .filter(candidate -> repo.course(candidate.courseId()).code().equalsIgnoreCase(course.code()))
                    .filter(candidate -> repo.course(candidate.courseId()).requiresLab())
                    .filter(candidate -> !candidate.id().equals(offering.id()))
                    .toList();
            for (CourseOffering candidate : sameCourseLabOfferings) {
                repo.offerings.remove(candidate);
                repo.offerings.add(new CourseOffering(candidate.id(), candidate.batchId(), candidate.courseId(),
                        candidate.lecturerId(), labInstructorId));
            }
        }
    }

    private String formatTeacherAvailability(Teacher teacher) {
        return teacher.availability().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> labelDay(entry.getKey()).substring(0, 3) + " " + shiftLabel(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String shiftLabel(Set<String> slots) {
        boolean morning = slots.contains("M1") || slots.contains("M2") || slots.contains("M3");
        boolean afternoon = slots.contains("A1") || slots.contains("A2");
        if (morning && afternoon) {
            return "Full day";
        }
        return afternoon ? "Afternoon" : "Morning";
    }

    private javafx.util.StringConverter<CourseOffering> courseOfferingConverter() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(CourseOffering value) {
                if (value == null) {
                    return "";
                }
                Course course = repo.course(value.courseId());
                return course.code() + " - " + course.name();
            }

            @Override
            public CourseOffering fromString(String value) {
                return repo.offerings.stream()
                        .filter(offering -> toString(offering).equals(value))
                        .findFirst()
                        .orElse(null);
            }
        };
    }

    private TableView<Teacher> teacherTable() {
        TableView<Teacher> table = new TableView<>();
        table.getItems().setAll(repo.teachers);
        TableColumn<Teacher, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        TableColumn<Teacher, String> role = new TableColumn<>("Role");
        role.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().role().name()));
        TableColumn<Teacher, String> days = new TableColumn<>("Working Days");
        days.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().availability().keySet().stream()
                .sorted()
                .map(day -> labelDay(day).substring(0, 3))
                .collect(Collectors.joining(", "))));
        TableColumn<Teacher, String> slots = new TableColumn<>("Availability");
        slots.setCellValueFactory(data -> new SimpleStringProperty(formatTeacherAvailability(data.getValue())));
        table.getColumns().addAll(name, role, days, slots);
        return table;
    }

    private TableView<Batch> batchTable(String programId) {
        TableView<Batch> table = new TableView<>();
        table.getItems().setAll(repo.batches.stream()
                .filter(batch -> batch.programId().equals(programId))
                .toList());
        TableColumn<Batch, String> name = new TableColumn<>("Batch");
        name.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        TableColumn<Batch, String> program = new TableColumn<>("Period");
        program.setCellValueFactory(data -> new SimpleStringProperty(repo.program(data.getValue().programId()).displayLabel()));
        TableColumn<Batch, String> sections = new TableColumn<>("Sections");
        sections.setCellValueFactory(data -> new SimpleStringProperty(repo.sections.stream()
                .filter(section -> section.batchId().equals(data.getValue().id()))
                .map(Section::name)
                .collect(Collectors.joining(", "))));
        TableColumn<Batch, String> students = new TableColumn<>("Students");
        students.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().studentCount())));
        table.getColumns().addAll(name, program, sections, students);
        return table;
    }

    private void showTeacher() {
        Teacher teacher = repo.teacherByUser(currentUser.id()).orElse(null);
        if (teacher == null) {
            VBox page = new VBox(14, header("Teacher View", "No teacher profile exists for this account yet."));
            page.getStyleClass().add("page");
            root.setCenter(page);
            return;
        }
        List<ScheduleItem> mine = repo.schedule.stream().filter(item -> item.teacherId().equals(teacher.id())).toList();
        VBox page = new VBox(14, header("Teacher View", "Personal weekly schedule"), weekView(repo.payloadFor(mine)));
        page.getStyleClass().add("page");
        root.setCenter(page);
    }

    private void showStudent() {
        Optional<StudentProfile> profile = repo.studentProfiles.stream()
                .filter(student -> student.userId().equals(currentUser.id()))
                .findFirst();
        if (profile.isEmpty()) {
            VBox page = new VBox(14, header("Student View", "No student profile linked to this account yet."));
            page.getStyleClass().add("page");
            root.setCenter(page);
            return;
        }
        List<ScheduleItem> mine = repo.schedule.stream()
                .filter(item -> profile.get().sectionId().equals(item.sectionId()) || profile.get().labGroupId().equals(item.labGroupId()))
                .toList();
        VBox page = new VBox(14, header("Student View", "Section theory and lab-group practicals"), weekView(repo.payloadFor(mine)));
        page.getStyleClass().add("page");
        root.setCenter(page);
    }

    private HBox header(String title, String subtitle) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label main = new Label(title);
        main.getStyleClass().add("page-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("muted");
        header.getChildren().addAll(main, sub);
        return header;
    }

    private ScrollPane weekView(CalendarPayload payload) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("calendar");
        grid.setGridLinesVisible(false);
        grid.getColumnConstraints().add(new ColumnConstraints(88));
        for (int i = 0; i < payload.days().size(); i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(15.2);
            grid.getColumnConstraints().add(col);
        }
        grid.getRowConstraints().add(new RowConstraints(38));
        for (int i = 0; i < payload.slots().size(); i++) {
            RowConstraints row = new RowConstraints(112);
            grid.getRowConstraints().add(row);
        }

        grid.add(cell("Time", "header-cell"), 0, 0);
        for (int c = 0; c < payload.days().size(); c++) {
            grid.add(cell(labelDay(payload.days().get(c)), "header-cell"), c + 1, 0);
        }
        for (int r = 0; r < payload.slots().size(); r++) {
            TimeSlot slot = payload.slots().get(r);
            grid.add(cell(slot.id() + "\n" + slot.start() + "-" + slot.end(), "time-cell"), 0, r + 1);
            for (int c = 0; c < payload.days().size(); c++) {
                VBox slotBox = new VBox(5);
                slotBox.getStyleClass().add("slot-cell");
                DayOfWeek day = payload.days().get(c);
                payload.events().stream()
                        .filter(event -> event.day() == day && event.slotId().equals(slot.id()))
                        .forEach(event -> slotBox.getChildren().add(eventNode(event)));
                grid.add(slotBox, c + 1, r + 1);
            }
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("calendar-scroll");
        return scroll;
    }

    private Label cell(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return label;
    }

    private VBox eventNode(CalendarEvent event) {
        VBox box = new VBox(3);
        box.getStyleClass().add("event");
        box.setStyle("-event-color: " + event.color() + ";");
        Label title = new Label(event.title());
        title.getStyleClass().add("event-title");
        Label detail = new Label(event.room() + " | " + event.teacher());
        detail.getStyleClass().add("event-detail");
        Label audience = new Label(event.audience());
        audience.getStyleClass().add("event-detail");
        box.getChildren().addAll(title, detail, audience);
        return box;
    }

    private TableView<ScheduleItem> detailsTable(List<ScheduleItem> items) {
        TableView<ScheduleItem> table = new TableView<>();
        table.setMaxHeight(180);
        table.getItems().setAll(items);
        TableColumn<ScheduleItem, String> course = new TableColumn<>("Course");
        course.setCellValueFactory(data -> {
            Course value = repo.course(data.getValue().courseId());
            return new SimpleStringProperty(value.code() + " - " + value.name());
        });
        TableColumn<ScheduleItem, String> kind = new TableColumn<>("Kind");
        kind.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().kind().name()));
        TableColumn<ScheduleItem, String> teacher = new TableColumn<>("Teacher");
        teacher.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().kind() == SessionKind.EXAM ? "Exam Board" : repo.teacher(data.getValue().teacherId()).name()));
        TableColumn<ScheduleItem, String> room = new TableColumn<>("Room");
        room.setCellValueFactory(data -> new SimpleStringProperty(repo.room(data.getValue().roomId()).name()));
        table.getColumns().addAll(course, kind, teacher, room);
        return table;
    }

    private String labelDay(DayOfWeek day) {
        String lower = day.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

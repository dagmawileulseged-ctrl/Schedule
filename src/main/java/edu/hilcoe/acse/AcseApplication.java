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
        filterType.setOnAction(event -> {
            selectedFilterType = filterType.getValue();
            selectedFilterValue = "All";
            populateFilterValues(selectedFilterType, filterValue);
        });
        Button applyFilter = new Button("Apply Filter");
        applyFilter.setOnAction(event -> {
            selectedFilterType = filterType.getValue();
            selectedFilterValue = filterValue.getValue() == null ? "All" : filterValue.getValue();
            showAdmin();
        });
        toolbar.getChildren().addAll(new Label("Admin Dashboard"), generate, clear, exam, filterType, filterValue, applyFilter);

        ListView<String> warnings = new ListView<>();
        warnings.setPrefHeight(130);
        warnings.getStyleClass().add("warnings");
        warnings.getItems().setAll(dashboardMessages);
        generate.setOnAction(event -> {
            SolverResult result = scheduler.generate();
            dashboardMessages = result.success()
                    ? result.warnings()
                    : result.suggestions().stream().map(suggestion -> "Conflict: " + suggestion).toList();
            showAdmin();
        });
        exam.setOnAction(event -> {
            if (repo.courses.isEmpty()) {
                dashboardMessages = List.of("Add at least one course assignment before applying an exam override.");
                showAdmin();
                return;
            }
            if (repo.schedule.isEmpty()) {
                scheduler.generate();
            }
            Course course = repo.courses.getFirst();
            Program program = repo.programs.getFirst();
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
        tabs.getTabs().add(tab("Teacher Assignments", teacherAssignmentPanel()));
        tabs.getTabs().add(tab("Batches", batchSetupPanel()));
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
        Label policy = new Label("Hard conflicts are never auto-forced. Adjust teacher availability, teacher assignment, course load, or batch setup, then generate again.");
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

    private List<ScheduleItem> filteredSchedule() {
        if (selectedFilterType.equals("All") || selectedFilterValue.equals("All")) {
            return List.copyOf(repo.schedule);
        }
        return repo.schedule.stream()
                .filter(item -> switch (selectedFilterType) {
                    case "Teacher" -> item.kind() != SessionKind.EXAM && repo.teacher(item.teacherId()).name().equals(selectedFilterValue);
                    case "Batch" -> item.kind() != SessionKind.EXAM
                            && repo.batch(repo.offering(item.offeringId()).batchId()).name().equals(selectedFilterValue);
                    case "Course" -> {
                        Course course = repo.course(item.courseId());
                        yield (course.code() + " - " + course.name()).equals(selectedFilterValue);
                    }
                    default -> true;
                })
                .toList();
    }

    private VBox teacherAssignmentPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("setup-panel");
        Label title = new Label("Teacher Assignment");
        title.getStyleClass().add("panel-title");

        TextField name = new TextField();
        name.setPromptText("Lecturer name");
        TextField email = new TextField();
        email.setPromptText("Lecturer email");
        ComboBox<String> shift = new ComboBox<>();
        shift.getItems().addAll("Morning", "Afternoon", "Full day");
        shift.setValue("Morning");

        HBox fields = new HBox(8, name, email, shift);
        fields.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Batch> batch = new ComboBox<>();
        batch.getItems().setAll(repo.batches);
        batch.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Batch value) {
                return value == null ? "" : value.name();
            }

            @Override
            public Batch fromString(String value) {
                return repo.batches.stream().filter(candidate -> candidate.name().equals(value)).findFirst().orElse(null);
            }
        });
        if (!repo.batches.isEmpty()) {
            batch.setValue(repo.batches.getFirst());
        }
        TextField courseCode = new TextField();
        courseCode.setPromptText("Course code");
        TextField courseName = new TextField();
        courseName.setPromptText("Course name");
        TextField labCount = new TextField("1");
        labCount.setPromptText("Lab/week");
        CheckBox needsTv = new CheckBox("Needs TV");
        CheckBox hasLab = new CheckBox("Has lab");
        hasLab.setSelected(true);
        HBox assignment = new HBox(8, batch, courseCode, courseName, labCount, hasLab, needsTv);
        assignment.setAlignment(Pos.CENTER_LEFT);
        HBox days = new HBox(8);
        List<CheckBox> dayChecks = new ArrayList<>();
        for (DayOfWeek day : Week.TEACHING_DAYS) {
            CheckBox check = new CheckBox(labelDay(day).substring(0, 3));
            check.setUserData(day);
            check.setSelected(day != DayOfWeek.SATURDAY);
            dayChecks.add(check);
            days.getChildren().add(check);
        }

        TextField labName = new TextField();
        labName.setPromptText("Lab instructor name");
        TextField labEmail = new TextField();
        labEmail.setPromptText("Lab instructor email");
        ComboBox<String> labShift = new ComboBox<>();
        labShift.getItems().addAll("Morning", "Afternoon", "Full day");
        labShift.setValue("Morning");
        HBox labFields = new HBox(8, labName, labEmail, labShift);
        labFields.setAlignment(Pos.CENTER_LEFT);
        HBox labDays = new HBox(8);
        List<CheckBox> labDayChecks = new ArrayList<>();
        for (DayOfWeek day : Week.TEACHING_DAYS) {
            CheckBox check = new CheckBox(labelDay(day).substring(0, 3));
            check.setUserData(day);
            check.setSelected(day != DayOfWeek.SATURDAY);
            labDayChecks.add(check);
            labDays.getChildren().add(check);
        }
        VBox labBox = new VBox(8, labFields, labDays);
        TitledPane labInstructorPane = new TitledPane("Lab Instructor", labBox);
        labInstructorPane.setExpanded(true);
        labInstructorPane.visibleProperty().bind(hasLab.selectedProperty());
        labInstructorPane.managedProperty().bind(hasLab.selectedProperty());

        Label status = new Label();
        status.getStyleClass().add("muted");
        Button add = new Button("Save Assignment");
        add.getStyleClass().add("primary-button");
        add.setOnAction(event -> {
            if (name.getText().isBlank() || email.getText().isBlank() || courseCode.getText().isBlank()
                    || courseName.getText().isBlank() || batch.getValue() == null) {
                status.setText("Enter lecturer, batch, course code, and course name first.");
                return;
            }
            Set<DayOfWeek> selectedDays = dayChecks.stream()
                    .filter(CheckBox::isSelected)
                    .map(check -> (DayOfWeek) check.getUserData())
                    .collect(Collectors.toSet());
            if (selectedDays.isEmpty()) {
                status.setText("Select at least one working day.");
                return;
            }
            Set<DayOfWeek> selectedLabDays = labDayChecks.stream()
                    .filter(CheckBox::isSelected)
                    .map(check -> (DayOfWeek) check.getUserData())
                    .collect(Collectors.toSet());
            if (hasLab.isSelected() && (labName.getText().isBlank() || labEmail.getText().isBlank() || selectedLabDays.isEmpty())) {
                status.setText("Enter lab instructor name, email, and working days.");
                return;
            }

            User user = User.of(UserRole.TEACHER, name.getText().trim(), email.getText().trim());
            Teacher lecturer = new Teacher(Ids.next(), user.id(), user.name(), TeacherRole.LECTURER, availabilityFor(selectedDays, shift.getValue()));
            repo.users.add(user);
            repo.teachers.add(lecturer);
            Teacher labInstructor = null;
            if (hasLab.isSelected()) {
                User labUser = User.of(UserRole.TEACHER, labName.getText().trim(), labEmail.getText().trim());
                labInstructor = new Teacher(Ids.next(), labUser.id(), labUser.name(), TeacherRole.LAB_INSTRUCTOR,
                        availabilityFor(selectedLabDays, labShift.getValue()));
                repo.users.add(labUser);
                repo.teachers.add(labInstructor);
            }
            Course course = upsertCourse(courseCode.getText().trim().toUpperCase(), courseName.getText().trim(),
                    hasLab.isSelected(), needsTv.isSelected(), 1,
                    hasLab.isSelected() ? parsePositiveInt(labCount.getText(), 1) : 0);
            upsertOffering(batch.getValue(), course, lecturer, labInstructor);
            repo.schedule.clear();
            dashboardMessages = List.of(lecturer.name() + " assigned to " + course.code() + " - " + course.name()
                    + " for " + batch.getValue().name() + ". Generate the schedule again to apply it.");
            showAdmin();
        });

        TableView<Teacher> teachers = teacherTable();
        teachers.setMaxHeight(130);
        panel.getChildren().addAll(title, fields, assignment, days, labInstructorPane, add, status, teachers);
        return panel;
    }

    private Course upsertCourse(String code, String name, boolean hasLab, boolean needsTv, int theoryCount, int labCount) {
        Course existing = repo.courses.stream()
                .filter(course -> course.code().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
        Course course = new Course(existing == null ? Ids.next() : existing.id(), code, name, hasLab, needsTv, theoryCount, labCount);
        if (existing != null) {
            repo.courses.remove(existing);
        }
        repo.courses.add(course);
        return course;
    }

    private void upsertOffering(Batch batch, Course course, Teacher lecturer, Teacher labInstructor) {
        CourseOffering existing = repo.offerings.stream()
                .filter(offering -> offering.batchId().equals(batch.id()) && offering.courseId().equals(course.id()))
                .findFirst()
                .orElse(null);
        String lecturerId = lecturer.id();
        String labInstructorId = course.requiresLab() && labInstructor != null ? labInstructor.id() : null;
        if (existing != null) {
            repo.offerings.remove(existing);
        }
        repo.offerings.add(new CourseOffering(Ids.next(), batch.id(), course.id(), lecturerId, labInstructorId));
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private VBox batchSetupPanel() {
        VBox box = new VBox(8);
        Label title = new Label("Batches");
        title.getStyleClass().add("panel-title");
        TextField batchName = new TextField();
        batchName.setPromptText("Batch code, e.g. DRB2504");
        ComboBox<ProgramType> programType = new ComboBox<>();
        programType.getItems().addAll(ProgramType.values());
        programType.setValue(ProgramType.SEMESTER);
        ComboBox<String> sections = new ComboBox<>();
        sections.getItems().addAll("No section", "Section A and B");
        sections.setValue("Section A and B");
        Button addBatch = new Button("Add Batch");
        addBatch.getStyleClass().add("primary-button");
        Label status = new Label();
        status.getStyleClass().add("muted");
        HBox fields = new HBox(8, batchName, programType, sections, addBatch);
        fields.setAlignment(Pos.CENTER_LEFT);

        addBatch.setOnAction(event -> {
            if (batchName.getText().isBlank()) {
                status.setText("Enter a batch code first.");
                return;
            }
            Program program = repo.programs.stream()
                    .filter(candidate -> candidate.type() == programType.getValue())
                    .findFirst()
                    .orElseThrow();
            List<String> sectionNames = sections.getValue().equals("No section")
                    ? List.of("Main")
                    : List.of("Section A", "Section B");
            Batch batch = SeedData.addBatch(repo, program, batchName.getText().trim().toUpperCase(), sectionNames);
            repo.schedule.clear();
            dashboardMessages = List.of(batch.name() + " added with " + String.join(", ", sectionNames)
                    + ". Add teacher/course assignments to include it in generation.");
            showAdmin();
        });

        TableView<Batch> batches = batchTable();
        batches.setMaxHeight(150);
        box.getChildren().addAll(title, fields, status, batches);
        return box;
    }

    private Map<DayOfWeek, Set<String>> availabilityFor(Set<DayOfWeek> days, String shift) {
        Map<DayOfWeek, Set<String>> availability = new EnumMap<>(DayOfWeek.class);
        Set<String> slots = switch (shift) {
            case "Afternoon" -> Set.of("A1", "A2");
            case "Full day" -> Set.of("M1", "M2", "M3", "A1", "A2");
            default -> Set.of("M1", "M2", "M3");
        };
        for (DayOfWeek day : days) {
            availability.put(day, slots);
        }
        return availability;
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
        TableColumn<Teacher, String> slots = new TableColumn<>("Slots");
        slots.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().availability().values().stream()
                .findFirst()
                .map(value -> String.join(", ", value))
                .orElse("None")));
        table.getColumns().addAll(name, role, days, slots);
        return table;
    }

    private TableView<Batch> batchTable() {
        TableView<Batch> table = new TableView<>();
        table.getItems().setAll(repo.batches);
        TableColumn<Batch, String> name = new TableColumn<>("Batch");
        name.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        TableColumn<Batch, String> program = new TableColumn<>("Program");
        program.setCellValueFactory(data -> new SimpleStringProperty(repo.program(data.getValue().programId()).type().name()));
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
        StudentProfile profile = repo.studentProfiles.stream()
                .filter(student -> student.userId().equals(currentUser.id()))
                .findFirst()
                .orElseThrow();
        List<ScheduleItem> mine = repo.schedule.stream()
                .filter(item -> profile.sectionId().equals(item.sectionId()) || profile.labGroupId().equals(item.labGroupId()))
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

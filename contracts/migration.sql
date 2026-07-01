CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT')),
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE teachers (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    role VARCHAR(30) NOT NULL CHECK (role IN ('LECTURER', 'LAB_INSTRUCTOR')),
    availability_json TEXT NOT NULL
);

CREATE TABLE rooms (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    type VARCHAR(20) NOT NULL CHECK (type IN ('LECTURE_ROOM', 'COMPUTER_LAB', 'LIBRARY')),
    equipment VARCHAR(20) NOT NULL CHECK (equipment IN ('TV_BOARD', 'BOARD_ONLY', 'COMPUTERS', 'LIBRARY'))
);

CREATE TABLE programs (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('TERM', 'SEMESTER')),
    academic_year VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_weeks INTEGER NOT NULL CHECK (total_weeks > 0),
    typical_min_courses INTEGER NOT NULL,
    typical_max_courses INTEGER NOT NULL
);

CREATE TABLE batches (
    id VARCHAR(36) PRIMARY KEY,
    program_id VARCHAR(36) NOT NULL REFERENCES programs(id),
    name VARCHAR(120) NOT NULL,
    student_count INTEGER NOT NULL CHECK (student_count > 0)
);

CREATE TABLE sections (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL REFERENCES batches(id),
    name VARCHAR(80) NOT NULL,
    size INTEGER NOT NULL CHECK (size > 0)
);

CREATE TABLE lab_groups (
    id VARCHAR(36) PRIMARY KEY,
    section_id VARCHAR(36) NOT NULL REFERENCES sections(id),
    name VARCHAR(80) NOT NULL,
    size INTEGER NOT NULL CHECK (size > 0)
);

CREATE TABLE courses (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(140) NOT NULL,
    requires_lab BOOLEAN NOT NULL,
    needs_tv BOOLEAN NOT NULL,
    weekly_theory_count INTEGER NOT NULL DEFAULT 0,
    weekly_lab_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE course_offerings (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL REFERENCES batches(id),
    course_id VARCHAR(36) NOT NULL REFERENCES courses(id),
    lecturer_id VARCHAR(36) NOT NULL REFERENCES teachers(id),
    lab_instructor_id VARCHAR(36) REFERENCES teachers(id)
);

CREATE TABLE schedule_base (
    id VARCHAR(36) PRIMARY KEY,
    course_offering_id VARCHAR(36) NOT NULL REFERENCES course_offerings(id),
    section_id VARCHAR(36) REFERENCES sections(id),
    lab_group_id VARCHAR(36) REFERENCES lab_groups(id),
    room_id VARCHAR(36) NOT NULL REFERENCES rooms(id),
    teacher_id VARCHAR(36) NOT NULL REFERENCES teachers(id),
    day_of_week VARCHAR(12) NOT NULL,
    slot_id VARCHAR(4) NOT NULL,
    session_kind VARCHAR(12) NOT NULL CHECK (session_kind IN ('THEORY', 'LAB')),
    CHECK ((section_id IS NOT NULL AND lab_group_id IS NULL) OR (section_id IS NULL AND lab_group_id IS NOT NULL))
);

CREATE TABLE schedule_overrides (
    id VARCHAR(36) PRIMARY KEY,
    base_schedule_id VARCHAR(36) NOT NULL REFERENCES schedule_base(id),
    new_room_id VARCHAR(36) REFERENCES rooms(id),
    new_day VARCHAR(12),
    new_slot VARCHAR(4),
    effective_date DATE NOT NULL,
    reason VARCHAR(255) NOT NULL
);

CREATE TABLE exams (
    id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL REFERENCES courses(id),
    program_id VARCHAR(36) NOT NULL REFERENCES programs(id),
    custom_start_time TIME NOT NULL,
    custom_end_time TIME NOT NULL
);

CREATE TABLE exam_assignments (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL REFERENCES exams(id),
    student_id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL REFERENCES rooms(id),
    seat_number VARCHAR(30)
);

package edu.uc.ltigradebook.model;

import lombok.Data;

@Data
public class GradeChangeEvent {
    private String student_id;
    private String assignment_name;
    private String grader_id;
    private String assignment_id;
    private String submission_id;
    private String grade;
    private String student_sis_id;
}

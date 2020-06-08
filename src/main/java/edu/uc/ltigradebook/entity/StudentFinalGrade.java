package edu.uc.ltigradebook.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@IdClass(StudentFinalGradeId.class)
@Table(name = "lti_gb_final_grades")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class StudentFinalGrade implements Serializable {

    private static final long serialVersionUID = 3481317298921322295L;

    @Id
    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Transient
    private String oldGrade;

}

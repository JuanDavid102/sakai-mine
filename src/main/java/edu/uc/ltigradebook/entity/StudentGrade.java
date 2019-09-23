package edu.uc.ltigradebook.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@IdClass(StudentGradeId.class)
@Table(name = "gradebook_grades")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class StudentGrade implements Serializable {

    private static final long serialVersionUID = 3481317298921322296L;

    @Id
    @Column(name = "assignment_id", nullable = false)
    private String assignmentId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "grade", nullable = false)
    private String grade;

}

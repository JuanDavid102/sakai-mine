package edu.uc.ltigradebook.entity;

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@IdClass(StudentGradeId.class)
@Table(name = "lti_gb_canvas_grades")
@AllArgsConstructor
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentCanvasGrade implements Serializable {

    private static final long serialVersionUID = 3481317298921322296L;

    @Id
    @Column(name = "assignment_id", nullable = false)
    private String assignmentId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Column(name = "created_date", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdDate;

    @Column(name = "modified_date")
    @LastModifiedDate
    private Instant modifiedDate;
}

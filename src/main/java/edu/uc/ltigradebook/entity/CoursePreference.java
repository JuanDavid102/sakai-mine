package edu.uc.ltigradebook.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gradebook_course_preferences")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class CoursePreference {

    @Id
    @Column(name = "course_id", nullable = false)
    private long courseId;

    @Column(name = "course_scale", nullable = false)
    private String conversionScale;

}

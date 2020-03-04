package edu.uc.ltigradebook.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lti_gb_course_prefs")
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

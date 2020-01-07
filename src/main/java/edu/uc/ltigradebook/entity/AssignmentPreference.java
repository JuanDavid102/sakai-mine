package edu.uc.ltigradebook.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gradebook_assignment_preferences")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class AssignmentPreference {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private long assignmentId;

    @Column(name = "assignment_scale", nullable = false)
    private String conversionScale;

}

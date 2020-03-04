package edu.uc.ltigradebook.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lti_gb_assignment_prefs")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class AssignmentPreference {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private long assignmentId;

    @Column(name = "assignment_scale", nullable = true)
    private String conversionScale;

    @Column(name = "assignment_muted", nullable = true)
    private Boolean muted;

}

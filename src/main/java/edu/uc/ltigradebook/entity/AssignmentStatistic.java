package edu.uc.ltigradebook.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lti_gb_assignment_statistics")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class AssignmentStatistic {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private long assignmentId;

    @Column(name = "average_score")
    private String averageScore;

    @Column(name = "highest_score")
    private String highestGrade;

    @Column(name = "lowest_score")
    private String lowestGrade;

    @Column(name = "submissions")
    private long submissions;

}

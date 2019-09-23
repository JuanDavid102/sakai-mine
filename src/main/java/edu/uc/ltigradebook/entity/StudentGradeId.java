package edu.uc.ltigradebook.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@EqualsAndHashCode
@NoArgsConstructor
public class StudentGradeId implements Serializable {

    private static final long serialVersionUID = 7342126421374958767L;

    private String assignmentId;
    private String userId;

}

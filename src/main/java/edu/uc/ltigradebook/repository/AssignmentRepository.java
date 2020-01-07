package edu.uc.ltigradebook.repository;

import edu.uc.ltigradebook.entity.AssignmentPreference;

import org.springframework.data.repository.CrudRepository;

public interface AssignmentRepository extends CrudRepository<AssignmentPreference, Long> {

}

package edu.uc.ltigradebook.repository;

import edu.uc.ltigradebook.entity.StudentFinalGrade;
import edu.uc.ltigradebook.entity.StudentFinalGradeId;

import org.springframework.data.repository.CrudRepository;

public interface FinalGradeRepository extends CrudRepository<StudentFinalGrade, StudentFinalGradeId> {

}

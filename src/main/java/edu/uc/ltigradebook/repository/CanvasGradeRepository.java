package edu.uc.ltigradebook.repository;

import edu.uc.ltigradebook.entity.StudentCanvasGrade;
import edu.uc.ltigradebook.entity.StudentGradeId;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CanvasGradeRepository extends CrudRepository<StudentCanvasGrade, StudentGradeId> {

}

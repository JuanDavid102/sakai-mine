package edu.uc.ltigradebook.repository;

import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.entity.StudentGradeId;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradeRepository extends CrudRepository<StudentGrade, StudentGradeId> {

    @Query(value = "select count(distinct(user_id)) from gradebook_grades", nativeQuery=true)
    Long getGradedUserCount();

}
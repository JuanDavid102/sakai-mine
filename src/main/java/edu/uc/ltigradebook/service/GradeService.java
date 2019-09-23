package edu.uc.ltigradebook.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.entity.StudentGradeId;
import edu.uc.ltigradebook.repository.GradeRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GradeService {

    @Autowired
    private GradeRepository gradeRepository;

    public Optional<StudentGrade> getGradeByAssignmentAndUser(String assignmentId, String userId) {
        log.debug("Getting a student grade by assignment {} and user {}.", assignmentId, userId);
        return gradeRepository.findById(new StudentGradeId(assignmentId, userId));
    }

    public void saveGrade(StudentGrade studentGrade) {
        log.info("Saving grade {} for the user {} and assignment {}.", studentGrade.getGrade(), studentGrade.getUserId(), studentGrade.getAssignmentId());
        gradeRepository.save(studentGrade);
        log.info("Grade saved successfully");
    }

    public void deleteGrade(StudentGrade studentGrade) {
        log.info("Deleting grade for the user {} and assignment {}.", studentGrade.getUserId(), studentGrade.getAssignmentId());
        gradeRepository.delete(studentGrade);
        log.info("Grade deleted successfully");
    }

    public long getGradeCount() {
        log.debug("Getting the grade count from the table.");
        return gradeRepository.count();
    }

    public long getGradedUserCount() {
        log.debug("Getting the graded user count from the table.");
        return gradeRepository.getGradedUserCount();
    }

}

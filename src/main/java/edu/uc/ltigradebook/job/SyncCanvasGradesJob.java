package edu.uc.ltigradebook.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.uc.ltigradebook.entity.StudentCanvasGrade;
import edu.uc.ltigradebook.repository.CanvasGradeRepository;
import edu.uc.ltigradebook.repository.CourseRepository;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SyncCanvasGradesJob {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    private CanvasGradeRepository canvasGradeRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 500)
    public void run() {
        log.info("Running Canvas Grades Synchronization process....");
        courseRepository.findAll().forEach(course -> {
            String courseId = String.valueOf(course.getCourseId());
            try {
                canvasService.getSingleCourse(courseId);
                canvasService.listCourseAssignments(courseId).forEach(assignment -> {
                    Integer assignmentId = assignment.getId();
                    try {
                        canvasService.getCourseSubmissions(courseId, assignmentId).forEach(assignmentSubmission -> {
                            String userId = assignmentSubmission.getUserId().toString();
                            String grade = assignmentSubmission.getGrade();
                            log.debug("Dumping grades from submission {}, user {}, assignment {} and course {}, grade is {}.", assignmentSubmission.getId(), userId, assignmentId, courseId, grade);
                            StudentCanvasGrade studentCanvasGrade = new StudentCanvasGrade();
                            studentCanvasGrade.setUserId(userId);
                            studentCanvasGrade.setGrade(grade);
                            studentCanvasGrade.setAssignmentId(assignment.getId().toString());
                            canvasGradeRepository.save(studentCanvasGrade);
                        });
                    } catch (Exception e) {
                        log.error("Fatal error getting course submissions from course {} and assignment {}.", courseId, assignmentId);
                    }
                });
            } catch (Exception e) {
                log.error("Fatal error getting course {}, skipping.", courseId);
            }
        });
        log.info("The Canvas Grades Synchronization process has ended and dumped all the canvas grades to the local DB.");
        
    }
}

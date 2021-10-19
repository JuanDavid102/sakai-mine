package edu.uc.ltigradebook.job;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${sync.grades.enabled:false}")
    private boolean enableGradeSyncJob;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    private CanvasGradeRepository canvasGradeRepository;

    @Scheduled(fixedDelayString = "${sync.grades.interval}", initialDelayString = "${sync.grades.initial.delay}")
    public void run() {
        if (!enableGradeSyncJob) {
            return;
        }
        log.info("Running Canvas Grades Synchronization process....");
        StopWatch stopwatch = StopWatch.createStarted();
        // Explores the courses present in the repository, basically synchronizes the courses present in the platform.
        courseRepository.findAll().forEach(course -> {
            String courseId = String.valueOf(course.getCourseId());
            try {
                // This trick is a courseId checker, it fails if the course does not exist in the Canvas instance, ideally when mixing instances or when a course is deleted.
                canvasService.getSingleCourse(courseId);
                // For each course present in the system, gets the assignments using the API.
                canvasService.listCourseAssignments(courseId).forEach(assignment -> {
                    Integer assignmentId = assignment.getId();
                    try {
                        // For each assignment present in the course, gets the course submissions.
                        canvasService.getCourseSubmissions(courseId, assignmentId).forEach(assignmentSubmission -> {
                            // For each grade present in the submission, stores the grade in the local DB.
                            String userId = assignmentSubmission.getUserId().toString();
                            String grade = assignmentSubmission.getGrade();
                            log.debug("Dumping grades from submission {}, user {}, assignment {} and course {}, grade is {}.", assignmentSubmission.getId(), userId, assignmentId, courseId, grade);
                            if (StringUtils.isBlank(grade)) {
                                return;
                            }
                            StudentCanvasGrade studentCanvasGrade = new StudentCanvasGrade();
                            studentCanvasGrade.setUserId(userId);
                            studentCanvasGrade.setGrade(grade);
                            studentCanvasGrade.setAssignmentId(assignment.getId().toString());
                            canvasGradeRepository.save(studentCanvasGrade);
                        });
                    } catch (Exception e) {
                        log.error("Fatal error getting course submissions from course {} and assignment {}. {}", courseId, assignmentId, e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.error("Fatal error getting course {}, skipping. {}", courseId, e.getMessage());
            }
        });
        stopwatch.stop();
        log.info("The Canvas Grades Synchronization process has been completed in {}.", stopwatch);        
    }
}

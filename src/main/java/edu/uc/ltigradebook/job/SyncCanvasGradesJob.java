package edu.uc.ltigradebook.job;

import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.uc.ltigradebook.repository.CourseRepository;
import edu.uc.ltigradebook.service.GradeService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SyncCanvasGradesJob {

    @Value("${sync.grades.enabled:false}")
    private boolean enableGradeSyncJob;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private GradeService gradeService;

    @Scheduled(cron = "${sync.grades.cron:0 0 1 * * *}")
    public void run() {
        if (!enableGradeSyncJob) {
            return;
        }
        log.info("Running Canvas Grades Synchronization process....");
        StopWatch stopwatch = StopWatch.createStarted();
        // Explores the courses present in the repository, basically synchronizes the courses present in the platform.
        courseRepository.findAll().forEach(course -> {
            String courseId = String.valueOf(course.getCourseId());
            gradeService.syncCourseGrades(courseId);
        });
        stopwatch.stop();
        log.info("The Canvas Grades Synchronization process has been completed in {}.", stopwatch);        
    }
}

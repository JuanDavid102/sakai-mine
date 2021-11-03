package edu.uc.ltigradebook.job;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVFormat.Builder;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.uc.ltigradebook.entity.StudentCanvasGrade;
import edu.uc.ltigradebook.model.CanvasGradeCsvRecord;
import edu.uc.ltigradebook.repository.CourseRepository;
import edu.uc.ltigradebook.service.AccountReportService;
import edu.uc.ltigradebook.service.GradeService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SyncCanvasSubmissionsJob {

    @Value("${sync.submissions.enabled:false}")
    private boolean enableSubmissionsSyncJob;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private AccountReportService accountReportService;

    @Autowired
    private GradeService gradeService;

    @Scheduled(fixedDelayString = "${sync.submissions.interval}", initialDelayString = "${sync.submissions.initial.delay}")
    public void run() throws Exception {
        if (!enableSubmissionsSyncJob) {
            return;
        }
        log.info("Running Canvas Submission Synchronization process....");
        StopWatch stopwatch = StopWatch.createStarted();
        List<StudentCanvasGrade> canvasGradeList = new ArrayList<>();
        // First let's collect all the courseIds to filter the account report by courseId.
        Set<String> courseSet = new HashSet<>();
        courseRepository.findAll().forEach(course -> {
            courseSet.add(String.valueOf(course.getCourseId()));
        });

        try {
            URI reportURI = accountReportService.getCanvasStudentSubmissionReport();
            try (final Reader reader = new InputStreamReader(new BOMInputStream(reportURI.toURL().openStream()), "UTF-8")) {
                Builder csvFormatBuilder = CSVFormat.Builder.create();
                csvFormatBuilder.setHeader();
                try(CSVParser csvParser = csvFormatBuilder.build().parse(reader)) {
                    csvParser.forEach(record -> {
                        CanvasGradeCsvRecord canvasGradeCsvRecord = new CanvasGradeCsvRecord(record);
                        // For each grade present in the submission, stores the grade in the local DB.
                        String userId = canvasGradeCsvRecord.getCanvasUserId();
                        String grade = canvasGradeCsvRecord.getScore();    
                        String courseId = canvasGradeCsvRecord.getCanvasCourseId();
                        String assignmentId = canvasGradeCsvRecord.getAssignmentId();
                        String submissionId = canvasGradeCsvRecord.getSubmissionId();
                        // Do not save the grade if it's empty of the course is not in use.
                        if (StringUtils.isBlank(grade) || !courseSet.contains(canvasGradeCsvRecord.getCanvasCourseId())) {
                            return;
                        }
                        StudentCanvasGrade studentCanvasGrade = new StudentCanvasGrade();
                        studentCanvasGrade.setUserId(userId);
                        studentCanvasGrade.setGrade(grade);
                        studentCanvasGrade.setAssignmentId(assignmentId);
                        canvasGradeList.add(studentCanvasGrade);
                        log.debug("Dumping grades from submission {}, user {}, assignment {} and course {}, grade is {}.", submissionId, userId, assignmentId, courseId, grade);
                    });
                }
            }
        } catch (Exception ex) {
            log.error("Fatal error getting the grades using account reports. {}", ex.getMessage());
        }
        log.info("All the grades from the report have been dumped, {} grades in total.", canvasGradeList.size());
        gradeService.saveCanvasGradeInBatch(canvasGradeList);
        stopwatch.stop();
        log.info("The Canvas Submission Synchronization process has been completed in {}.", stopwatch);
    }
}

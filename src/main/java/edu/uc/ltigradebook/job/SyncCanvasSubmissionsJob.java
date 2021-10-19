package edu.uc.ltigradebook.job;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.HashSet;
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
import edu.uc.ltigradebook.repository.CanvasGradeRepository;
import edu.uc.ltigradebook.repository.CourseRepository;
import edu.uc.ltigradebook.service.AccountReportService;
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
    private CanvasGradeRepository canvasGradeRepository;

    @Scheduled(fixedDelayString = "${sync.submissions.interval}", initialDelayString = "${sync.submissions.initial.delay}")
    public void run() throws Exception {
        if (!enableSubmissionsSyncJob) {
            return;
        }
        log.info("Running Canvas Submission Synchronization process....");

        StopWatch stopwatch = StopWatch.createStarted();

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
                        if (StringUtils.isBlank(grade)) {
                            return;
                        }
                        if (!courseSet.contains(canvasGradeCsvRecord.getCanvasCourseId())) {
                            return;
                        }
                        StudentCanvasGrade studentCanvasGrade = new StudentCanvasGrade();
                        studentCanvasGrade.setUserId(userId);
                        studentCanvasGrade.setGrade(grade);
                        studentCanvasGrade.setAssignmentId(assignmentId);
                        canvasGradeRepository.save(studentCanvasGrade);
                        log.debug("Dumping grades from submission {}, user {}, assignment {} and course {}, grade is {}.", submissionId, userId, assignmentId, courseId, grade);
                    });
                }
            }
        } catch (Exception ex) {
            log.error("Fatal error getting the grades using account reports. {}", ex.getMessage());
        }
        stopwatch.stop();
        log.info("The Canvas Submission Synchronization process has been completed in {}.", stopwatch);
    }
}

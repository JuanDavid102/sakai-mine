package edu.uc.ltigradebook.controller;

import edu.ksu.canvas.model.Enrollment;
import edu.ksu.canvas.model.Section;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstant;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.CourseService;
import edu.uc.ltigradebook.util.GradeUtils;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.service.AssignmentService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.GradeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
public class CsvController {

    @Autowired
    private CanvasAPIServiceWrapper canvasService;
    
    @Autowired
    private CourseService courseService;

    @Autowired
    private EventTrackingService eventTrackingService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private MessageSource messageSource;

    private static final char CSV_SEPARATOR = ';';
    private static final String BOM = "\uFEFF";
    private static final String EXPORT_ALL_SECTION_VALUE = "all";
    private static final String GRADE_NOT_AVAILABLE = "-";

    @GetMapping("/export_csv")
    public ResponseEntity<Resource> exportToCsv(LtiSession ltiSession, @RequestParam String sectionId) throws GradeException {
        File tempFile;
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        boolean exportAllSection = (EXPORT_ALL_SECTION_VALUE.equals(sectionId));

        try {
            tempFile = File.createTempFile("gradebookTemplate", ".csv");

            //CSV separator is comma unless the comma is the decimal separator, then is ;
            try (OutputStreamWriter fstream = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8.name())){
                fstream.write(BOM);
                CSVWriter csvWriter = new CSVWriter(fstream, ".".equals(",") ? CSVWriter.DEFAULT_SEPARATOR : CSV_SEPARATOR, CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END);

                // Create csv header
                List<String> header = new ArrayList<>();
                header.add(messageSource.getMessage("csv_header_student", null, LocaleContextHolder.getLocale()));
                header.add(messageSource.getMessage("csv_header_id", null, LocaleContextHolder.getLocale()));
                header.add(messageSource.getMessage("csv_header_sis_login_id", null, LocaleContextHolder.getLocale()));
                header.add(messageSource.getMessage("csv_header_section", null, LocaleContextHolder.getLocale()));
                header.add(messageSource.getMessage("csv_header_nrc", null, LocaleContextHolder.getLocale()));

                // get list of assignments. this allows us to build the columns and then fetch the grades for each student for each assignment from the map
                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);

                // get sections
                List<Section> sectionList = canvasService.getSectionsInCourse(courseId);
                Map<String, String> sectionNameMap = new HashMap<>();
                Map<String, String> sectionNrcMap = new HashMap<>();
                for(Section section : sectionList) {
                    sectionNameMap.put(String.valueOf(section.getId()), section.getName());
                    String sisSectionId = section.getSisSectionId();
                    if (sisSectionId == null)  {
                        sectionNrcMap.put(String.valueOf(section.getId()), "");
                    } else {
                        String[] splittedSectionId = sisSectionId.split("-");
                        String nrcCode = splittedSectionId[1];
                        sectionNrcMap.put(String.valueOf(section.getId()), nrcCode);
                    }
                }

                // build column header
                Map<String, List<Submission>> submissionsMap = new HashMap<>();
                ExecutorService executorService = Executors.newCachedThreadPool();
                for (Assignment assignment : assignmentList) {
                    String assignmentId = String.valueOf(assignment.getId());
                    header.add(messageSource.getMessage("csv_header_assignment", new Object[]{assignment.getName(), assignmentId}, LocaleContextHolder.getLocale()));
                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                submissionsMap.put(assignmentId, canvasService.getCourseSubmissions(courseId, assignment.getId()));
                            } catch (IOException ex) {
                                log.error("Cannot get course submissions por course: {} and assignment: {}.", courseId, assignment.getId(), ex);
                            }
                            }
                        });
                }
                
                executorService.shutdown();
                //Wait until all the submission requests end.
                try {
                    executorService.awaitTermination(2, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    log.error("The submissions thread has been interrupted, aborting.", e);
                }

                List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
                for(AssignmentGroup assignmentGroup : assignmentGroupList) {
                    header.add(assignmentGroup.getName());
                }
                header.add(messageSource.getMessage("shared_current_grade", new Object[]{}, LocaleContextHolder.getLocale()));
                header.add(messageSource.getMessage("shared_final_grade", new Object[]{}, LocaleContextHolder.getLocale()));

                csvWriter.writeNext(header.toArray(new String[] {}));

                List<User> userList = canvasService.getUsersInCourse(courseId);
                for (User user : userList) {

                    //Fill the section
                    String section = StringUtils.EMPTY;
                    String nrc = StringUtils.EMPTY;
                    boolean exportSection = false;
                    if(user.getEnrollments() != null && !user.getEnrollments().isEmpty()) {
                        StringJoiner joiner = new StringJoiner(",");
                        StringJoiner joinerNrc = new StringJoiner(",");
                        for(Enrollment enrollment : user.getEnrollments()) {
                            joiner.add(sectionNameMap.get(enrollment.getCourseSectionId()));
                            joinerNrc.add(sectionNrcMap.get(enrollment.getCourseSectionId()));
                            if (sectionId.equals(enrollment.getCourseSectionId())) exportSection = true;
                        }
                        section = joiner.toString();
                        nrc = joinerNrc.toString();
                    }

                    if (exportAllSection || exportSection) {
                        String sisUserId = user.getSisUserId();
                        String userId = String.valueOf(user.getId());
                        final List<String> line = new ArrayList<>();
                        line.add(user.getSortableName());
                        line.add(String.valueOf(user.getId()));
                        line.add(StringUtils.isNotBlank(sisUserId) ? sisUserId : userId);
                        line.add(section);
                        line.add(nrc);
                        for (Assignment assignment : assignmentList) {
                            String assignmentId = String.valueOf(assignment.getId());
                            String grade;
                            Map<String, Object> cellSettings = new HashMap<>();
                            //Get the grade from persistence, get the grade from the API otherwise.
                            Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, userId);
                            if (overwrittenStudentGrade.isPresent()) {
                                grade = overwrittenStudentGrade.get().getGrade();
                                cellSettings.put("overwrittenGrade", (overwrittenStudentGrade.isPresent() && StringUtils.isNotBlank(grade)));
                            } else {
                                List<Submission> submissionsForAssignment = submissionsMap.get(assignmentId);
                                Optional<Submission> optionalGrade = submissionsForAssignment.stream()
                                        .filter(submission -> submission.getUserId() != null && userId.equals(submission.getUserId().toString()))
                                        .findAny();
                                grade = optionalGrade.isPresent() ? optionalGrade.get().getGrade() : StringUtils.EMPTY;                                

                                String assignmentConversionScale = coursePreference.getConversionScale();
                                Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                                if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                                    assignmentConversionScale = assignmentPreference.get().getConversionScale();
                                }

                                //Grade conversion logic
                                switch (assignment.getGradingType()) {
                                    case GradeUtils.GRADE_TYPE_POINTS:
                                        grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible().toString());
                                        break;
                                    case GradeUtils.GRADE_TYPE_PERCENT:
                                        grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                                        break;
                                    default:
                                        grade = GRADE_NOT_AVAILABLE;
                                        break;
                                }
                            }
                            line.add(grade);
                        }
                        for(AssignmentGroup assignmentGroup : assignmentGroupList) {
                            line.add(gradeService.getStudentGroupMean(ltiSession, Long.parseLong(assignmentGroup.getId().toString()), user.getId(), null));
                        }
                        line.add(gradeService.getStudentTotalMean(ltiSession, user.getId(), true, null));
                        line.add(gradeService.getStudentTotalMean(ltiSession, user.getId(), false, null));
                        csvWriter.writeNext(line.toArray(new String[] {}));
                    }
                }
                csvWriter.close();
            }

        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        ByteArrayResource resource;
        try(FileInputStream fis = new FileInputStream(tempFile)) {
            resource = new ByteArrayResource(IOUtils.toByteArray(fis));
        } catch(IOException e) {
            return null;
        }

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(new MediaType("application", "csv"));
        respHeaders.setContentLength(12345678);
        respHeaders.setContentDispositionFormData("attachment", "file.csv");
        return ResponseEntity.ok()
                .headers(respHeaders)
                .contentLength(tempFile.length())
                .contentType(MediaType.parseMediaType("application/csv"))
                .body(resource);
    }

    @PostMapping("/import_csv")
    public ResponseEntity<String> importCsv(@RequestParam MultipartFile file, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) {
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        boolean validCsv = false;
        boolean errors = false;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVParser parser = new CSVParserBuilder().withSeparator(CSV_SEPARATOR).build();
            CSVReader csvReader = new CSVReaderBuilder(br).withCSVParser(parser).build();
            List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
            Map<String, List<Submission>> submissionsMap = new HashMap<>();
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Assignment assignment : assignmentList) {
                String assignmentId = String.valueOf(assignment.getId());
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            submissionsMap.put(assignmentId, canvasService.getCourseSubmissions(courseId, assignment.getId()));
                         } catch (IOException ex) {
                            log.error("Cannot get course submissions por course: {} and assignment: {}.", courseId, assignment.getId(), ex);
                        }
                        }
                    });
            }

            executorService.shutdown();
            //Wait until all the submission requests end.
            try {
                executorService.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("The submissions thread has been interrupted, aborting.", e);
            }

            List<String[]> all = csvReader.readAll();
            String[] header = all.get(0);
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
            int rightColsIgnore = assignmentGroupList.size() + 2;
            for (int i = 5; i < header.length - rightColsIgnore; i++) {
                String assignmentId = StringUtils.substringBetween(header[i], "(", ")");
                Assignment assignment = assignmentList.stream().filter(a -> assignmentId.equals(String.valueOf(a.getId()))).findFirst().get();
                List<Submission> submissionsForAssignment = submissionsMap.get(assignmentId);
                for (int z = 1; z < all.size(); z++) {
                    try {
                        String studentId = all.get(z)[1];
                        String newGrade = all.get(z)[i];
                        JSONObject eventDetails = new JSONObject().put("assignmentId", assignmentId).put("userId", studentId).put("newGrade", newGrade);
                        boolean saveGrade = false;
                        boolean deleteGrade = false;
                        boolean sameGrade = false;

                        Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, studentId);
                        if (overwrittenStudentGrade.isPresent()){
                            String overwrittenGrade = overwrittenStudentGrade.get().getGrade();
                            eventDetails.put("previousGrade", overwrittenGrade);
                            if (!newGrade.equals(overwrittenGrade)) saveGrade = true;
                            else sameGrade = true;
                            if (StringUtils.isBlank(newGrade)) {
                                deleteGrade = true;
                                saveGrade = false;
                            }

                        } else {
                            Optional<Submission> optionalGrade = submissionsForAssignment.stream()
                                    .filter(submission -> submission.getUserId() != null && studentId.equals(submission.getUserId().toString()))
                                    .findAny();
                            if (optionalGrade.isPresent()) {
                                String grade = optionalGrade.get().getGrade();

                                String assignmentConversionScale = coursePreference.getConversionScale();
                                Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                                if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                                    assignmentConversionScale = assignmentPreference.get().getConversionScale();
                                }

                                //Grade conversion logic
                                switch (assignment.getGradingType()) {
                                    case GradeUtils.GRADE_TYPE_POINTS:
                                        grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible().toString());
                                        break;
                                    case GradeUtils.GRADE_TYPE_PERCENT:
                                        grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                                        break;
                                    default:
                                        grade = GRADE_NOT_AVAILABLE;
                                        break;
                                }
                                eventDetails.put("previousGrade", grade);
                                if (!newGrade.equals(grade) && StringUtils.isNotBlank(newGrade))
                                    saveGrade = true;
                                else sameGrade = true;

                            } else eventDetails.put("previousGrade", GRADE_NOT_AVAILABLE);
                        }

                        //If new grade is invalid, skip saving process
                        if (StringUtils.isBlank(newGrade)) {
                        } else if (!GradeUtils.isValidGrade(newGrade)) {
                            if (!sameGrade) errors = true;
                            continue;
                        }

                        StudentGrade studentGrade = new StudentGrade();
                        studentGrade.setAssignmentId(assignmentId);
                        studentGrade.setUserId(studentId);

                        if (deleteGrade) {
                            eventTrackingService.postEvent(EventConstant.IMPORT_DELETE_GRADE, ltiPrincipal.getUser(), courseId, eventDetails.toString());
                            gradeService.deleteGrade(studentGrade);

                        } else if (saveGrade) {
                            eventTrackingService.postEvent(EventConstant.IMPORT_POST_GRADE, ltiPrincipal.getUser(), courseId, eventDetails.toString());
                            studentGrade.setGrade(newGrade);
                            gradeService.saveGrade(studentGrade);
                        }
                        validCsv = true;

                    } catch(Exception ex) {
                        errors = true;
                        log.error("Cannot save grade on {} assignment", assignmentId);
                    }
                }
            }
        } catch (Exception ex) {
            validCsv = false;
            log.error("Error reading csv file");
        }

        if (validCsv) {
            return new ResponseEntity<>(String.valueOf(errors), HttpStatus.OK);
        } else return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

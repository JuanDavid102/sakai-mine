package edu.uc.ltigradebook.controller;

import edu.ksu.canvas.model.Enrollment;
import edu.ksu.canvas.model.Section;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.StudentCanvasGrade;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.service.AssignmentService;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.CourseService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.GradeService;
import edu.uc.ltigradebook.service.SecurityService;
import edu.uc.ltigradebook.util.GradeUtils;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
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

@Slf4j
@Controller
public class CsvController {

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private EventTrackingService eventTrackingService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SecurityService securityService;

    private static final char CSV_SEMICOLON_SEPARATOR = ';';
    private static final char CSV_COMMA_SEPARATOR = ',';
    private static final char CSV_TAB_SEPARATOR = '\t';
    private static final String BOM = "\uFEFF";
    private static final String EXPORT_ALL_SECTION_VALUE = "all";
    private static final String GRADE_NOT_AVAILABLE = "-";

    @GetMapping("/export_csv")
    public ResponseEntity<Resource> exportToCsv(LtiSession ltiSession, @RequestParam String sectionId) throws GradeException {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).put("sectionId", sectionId).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying export to CSV, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            return null;
        }

        File tempFile;
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        boolean exportAllSection = (EXPORT_ALL_SECTION_VALUE.equals(sectionId));

        try {
            tempFile = File.createTempFile("gradebookTemplate", ".csv");

            //CSV separator is comma unless the comma is the decimal separator, then is ;
            try (OutputStreamWriter fstream = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8.name())){
                fstream.write(BOM);
                CSVWriter csvWriter = new CSVWriter(fstream, CSV_SEMICOLON_SEPARATOR, CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END);

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
                for (Assignment assignment : assignmentList) {
                    String assignmentId = String.valueOf(assignment.getId());
                    header.add(messageSource.getMessage("csv_header_assignment", new Object[]{assignment.getName().replace(",","."), assignmentId}, LocaleContextHolder.getLocale()));
                }

                List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
                for(AssignmentGroup assignmentGroup : assignmentGroupList) {
                    header.add(String.format("%s (%s%%)", assignmentGroup.getName(), assignmentGroup.getGroupWeight().toString()));
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
                        StringJoiner joiner = new StringJoiner(". ");
                        StringJoiner joinerNrc = new StringJoiner(". ");
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
                        line.add(user.getSortableName().replace(",","."));
                        line.add(String.valueOf(user.getId()));
                        line.add(StringUtils.isNotBlank(sisUserId) ? sisUserId : userId);
                        line.add(section.replace(",","."));
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
                            	// Get the Canvas grade from the local DB instead of polling Canvas.
                                Optional<StudentCanvasGrade> optionalGrade = gradeService.getCanvasGradeByAssignmentAndUser(assignmentId, userId);
                                grade = optionalGrade.isPresent() ? optionalGrade.get().getGrade() : StringUtils.EMPTY;

                                String assignmentConversionScale = coursePreference.getConversionScale();
                                Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                                if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                                    assignmentConversionScale = assignmentPreference.get().getConversionScale();
                                }

                                //Grade conversion logic
                                switch (assignment.getGradingType()) {
                                    case GradeUtils.GRADE_TYPE_POINTS:
                                        grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible());
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
                            line.add(gradeService.getStudentGroupMean(ltiSession, Long.parseLong(assignmentGroup.getId().toString()), user.getId(), null).getString("grade"));
                        }
                        line.add(gradeService.getStudentTotalMean(ltiSession, user.getId(), true, null).getString("grade"));
                        line.add(gradeService.getStudentTotalMean(ltiSession, user.getId(), false, null).getString("grade"));
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
    public ResponseEntity<String> importCsv(@RequestParam MultipartFile file, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Character csvSeparator) {
        if (csvSeparator == null) csvSeparator = CSV_SEMICOLON_SEPARATOR;
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying import from CSV, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            return null;
        }

        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        boolean validCsv = true;
        boolean errors = false;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVParser parser = new CSVParserBuilder().withSeparator(csvSeparator).withStrictQuotes(false).build();
            CSVReader csvReader = new CSVReaderBuilder(br).withCSVParser(parser).build();
            List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
            List<String[]> all = csvReader.readAll();
            String[] header = all.get(0);
            // If cols are missing, try with other separator
            if (header.length < 2 && csvSeparator == CSV_SEMICOLON_SEPARATOR) {
                return this.importCsv(file, ltiPrincipal, ltiSession, CSV_COMMA_SEPARATOR);
            }
            if (header.length < 2 && csvSeparator == CSV_COMMA_SEPARATOR) {
                return this.importCsv(file, ltiPrincipal, ltiSession, CSV_TAB_SEPARATOR);
            }
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
            int rightColsIgnore = assignmentGroupList.size() + 2;
            for (int i = 5; i < header.length - rightColsIgnore; i++) {
                String assignmentId = StringUtils.substringBetween(header[i], "(", ")");
                Assignment assignment = assignmentList.stream().filter(a -> assignmentId.equals(String.valueOf(a.getId()))).findFirst().get();
                for (int z = 1; z < all.size(); z++) {
                    String studentId = all.get(z)[1];
                    try {
                        String newGrade = all.get(z)[i];

                        newGrade = StringUtils.replace(newGrade, ",", ".");
                        JSONObject eventDetailsJson = new JSONObject().put("assignmentId", assignmentId).put("userId", studentId).put("grade", newGrade);
                        boolean saveGrade = false;
                        boolean deleteGrade = false;
                        boolean sameGrade = false;

                        Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, studentId);
                        if (overwrittenStudentGrade.isPresent()){
                            String overwrittenGrade = overwrittenStudentGrade.get().getGrade();
                            eventDetailsJson.put("oldGrade", overwrittenGrade);
                            if (!newGrade.equals(overwrittenGrade)) saveGrade = true;
                            else sameGrade = true;
                            if (StringUtils.isBlank(newGrade)) {
                                deleteGrade = true;
                                saveGrade = false;
                            }

                        } else {
                        	// Get the Canvas grade from the DB instead of polling Canvas.
                            Optional<StudentCanvasGrade> optionalGrade = gradeService.getCanvasGradeByAssignmentAndUser(assignmentId, studentId);
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
                                        grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible());
                                        break;
                                    case GradeUtils.GRADE_TYPE_PERCENT:
                                        grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                                        break;
                                    default:
                                        grade = GRADE_NOT_AVAILABLE;
                                        break;
                                }
                                eventDetailsJson.put("oldGrade", grade);
                                if (!newGrade.equals(grade) && StringUtils.isNotBlank(newGrade))
                                    saveGrade = true;
                                else sameGrade = true;

                            } else eventDetailsJson.put("oldGrade", GRADE_NOT_AVAILABLE);
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
                            eventTrackingService.postEvent(EventConstants.IMPORT_DELETE_GRADE, canvasUserId, courseId, eventDetailsJson.toString());
                            gradeService.deleteGrade(studentGrade);
                        } else if (saveGrade) {
                            eventTrackingService.postEvent(EventConstants.IMPORT_POST_GRADE, canvasUserId, courseId, eventDetailsJson.toString());
                            newGrade = GradeUtils.roundGrade(newGrade);
                            studentGrade.setGrade(newGrade);
                            gradeService.saveGrade(studentGrade);
                        }

                        validCsv = true;

                    } catch(Exception ex) {
                        errors = true;
                        log.error("Cannot save grade on {} assignment {} studentId", assignmentId, studentId);
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

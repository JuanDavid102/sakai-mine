package edu.uc.ltigradebook.controller;

import edu.ksu.canvas.model.Account;
import edu.ksu.canvas.model.Course;
import edu.ksu.canvas.model.Enrollment;
import edu.ksu.canvas.model.Section;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.lti.launch.model.InstitutionRole;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.constants.TemplateConstants;
import edu.uc.ltigradebook.dao.BannerServiceDao;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.AssignmentStatistic;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.Event;
import edu.uc.ltigradebook.entity.StudentFinalGrade;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.service.AccountService;
import edu.uc.ltigradebook.service.AssignmentService;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.CourseService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.GradeService;
import edu.uc.ltigradebook.service.SecurityService;
import edu.uc.ltigradebook.service.TokenService;
import edu.uc.ltigradebook.util.GradeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.time.StopWatch;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
public class IndexController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private BannerServiceDao bannerServiceDao;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private EventTrackingService eventTrackingService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SessionLocaleResolver localeResolver;

    @Autowired
    private TokenService tokenService;

    @Value("${lti-gradebook.url:someurl}")
    private String canvasBaseUrl;

    private static final String GRADE_NOT_AVAILABLE = "-";
    private static final String[] ASSIGNMENT_STATISTIC_INVALID_VALUES = new String[]{"","-","A+","A","A-","B+","B","B-","C+","C","C-","D+","D","D-","F"};
    private static final String SPEED_GRADER_URL = "%s/courses/%s/gradebook/speed_grader?assignment_id=%s";

    @GetMapping("/")
    public ModelAndView index(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        return new ModelAndView(TemplateConstants.INDEX_TEMPLATE);
    }

    @GetMapping("/index")
    public ModelAndView index(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model, @RequestParam(required=false) Boolean errors) {
        try {
            LtiLaunchData lld = ltiSession.getLtiLaunchData();
            log.debug("Debugging ltiLaunch Object:\n"+ReflectionToStringBuilder.toString(lld));
            String canvasLoginId = ltiPrincipal.getUser();
            String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
            String courseId = ltiSession.getCanvasCourseId();
            model.addAttribute("courseId", courseId);
            model.addAttribute("errors", errors);

            eventTrackingService.postEvent(EventConstants.LTI_LOGIN, canvasUserId, courseId);

            if(lld.getRolesList() == null || lld.getRolesList().isEmpty()) {
                throw new Exception(String.format("The user %s doesn't have any valid role.", canvasLoginId));
            }

            //Assume that the contextId should be a Long value, if not the tool is being executed in a special context.
            try {
                Long.parseLong(courseId);
            } catch(Exception e) {
                if(securityService.isAdminUser(canvasLoginId, lld.getRolesList())) {
                    return adminMain(ltiPrincipal, ltiSession, model);
                }
            }

            if (securityService.isFaculty(lld.getRolesList())) {
                return handleInstructorView(ltiPrincipal, ltiSession, model);
            }

            if(securityService.isStudent(lld.getRolesList())) {
                return handleStudentView(ltiPrincipal, ltiSession, model);
            }

        } catch(Exception ex) {
            log.error("Error displaying the LTI tool content.", ex);
        }

        return new ModelAndView(TemplateConstants.ERROR_TEMPLATE);
    }

    private ModelAndView handleInstructorView(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        log.debug("The user {} with id {} is entering the instructor view.", canvasLoginId, canvasUserId);

        try {
            StopWatch stopwatch = StopWatch.createStarted();
            List<User> userList = canvasService.getUsersInCourse(courseId);
            stopwatch.stop();
            log.debug("getUsersInCourse took {} for {} users.", stopwatch, userList.size());
            
            stopwatch.reset();
            stopwatch.start();
            List<Section> sectionList = canvasService.getSectionsInCourse(courseId);
            Map<String, String> sectionMap = new HashMap<String, String>();
            for(Section section : sectionList) {
                sectionMap.put(String.valueOf(section.getId()), section.getName());
            }
            stopwatch.stop();
            log.debug("getSectionsInCourse took {} for {} sections.", stopwatch, sectionList.size());

            stopwatch.reset();
            stopwatch.start();
            Optional<Course> course = canvasService.getSingleCourse(courseId);
            String exportFileName, courseName = "";
            if (course.isPresent()) {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HHmm");
                exportFileName = messageSource.getMessage("instructor_export_file_name", new String[] {format.format(new Date()), course.get().getCourseCode()}, LocaleContextHolder.getLocale());
                courseName = course.get().getName();
            } else {
                exportFileName = messageSource.getMessage("instructor_export_file_name_default", null, LocaleContextHolder.getLocale());
            }
            model.addAttribute("exportFileName", exportFileName);
            model.addAttribute("courseName", courseName);
            stopwatch.stop();
            log.debug("getSingleCourse took {}.", stopwatch);

            stopwatch.reset();
            stopwatch.start();
            List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
            stopwatch.stop();
            log.debug("listCourseAssignments took {} for {} assignments.", stopwatch, assignmentList.size());
            
            stopwatch.reset();
            stopwatch.start();
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
            stopwatch.stop();
            log.debug("listAssignmentGroups took {} for {} groups.", stopwatch, assignmentGroupList.size());

            stopwatch.reset();
            stopwatch.start();
            Map<String, List<Submission>> submissionsMap = new HashMap<String, List<Submission>>();
            List<String> tableHeaderList = new ArrayList<>();
            List<List<Map<String, Object>>> cellRowListSettings = new ArrayList<>();
            tableHeaderList.add(messageSource.getMessage("instructor_header_login", null, LocaleContextHolder.getLocale()));
            tableHeaderList.add(messageSource.getMessage("instructor_header_student_name", null, LocaleContextHolder.getLocale()));
            tableHeaderList.add(messageSource.getMessage("instructor_header_section", null, LocaleContextHolder.getLocale()));
            final int LEFT_READ_ONLY_COLS = 3;
            List<Map<String, Object>> colSettings = new ArrayList<>();
            colSettings.add(new HashMap<>());
            colSettings.add(new HashMap<>());
            colSettings.add(new HashMap<>());
            Map<Integer, Boolean> assignmentMutedMap = new HashMap<>();
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Assignment assignment : assignmentList) {
                Map<String, Object> cellSettings = new HashMap<>();
                String assignmentId = String.valueOf(assignment.getId());
                Optional<AssignmentPreference> assignmentPref = assignmentService.getAssignmentPreference(assignmentId);
                tableHeaderList.add(assignment.getName());

                boolean assignmentIsMuted = false;
                if (StringUtils.isNotBlank(assignment.getMuted())) {
                    assignmentIsMuted = Boolean.valueOf(assignment.getMuted());
                }
                if (assignmentPref.isPresent() && assignmentPref.get().getMuted() != null) {
                    assignmentIsMuted = assignmentPref.get().getMuted();
                }
                assignmentMutedMap.put(assignment.getId(), assignmentIsMuted);

                boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                cellSettings.put("omitFromFinalGrade", omitFromFinalGrade);
                cellSettings.put("assignmentIsMuted", assignmentIsMuted);
                cellSettings.put("assignmentName", assignment.getName());
                cellSettings.put("assignmentId", assignment.getId());
                cellSettings.put("speedGraderUrl", String.format(SPEED_GRADER_URL, canvasBaseUrl, courseId, assignmentId));
                colSettings.add(cellSettings);
                
                //Get the assigment submissions in a separated thread.
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            submissionsMap.put(assignmentId, canvasService.getCourseSubmissions(courseId, assignment.getId()));
                        } catch (IOException e) {
                            log.error("Error getting assignment submissions for the course {} and the assignment {}.", courseId, assignmentId, e);
                        }
                    }
                });

            }
            cellRowListSettings.add(colSettings);
            
            executorService.shutdown();
            //Wait until all the submission requests end.
            try {
                executorService.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("The submissions thread has been interrupted, aborting.", e);
            }            
            
            JSONObject assignmentGroupArray = new JSONObject();
            BigDecimal assignmentGroupTotalWeight = BigDecimal.ZERO;
            int RIGHT_READ_ONLY_COLS = 0;
            for(AssignmentGroup assignmentGroup : assignmentGroupList) {
                JSONObject jsonAssignmentGroup = new JSONObject();
                jsonAssignmentGroup.put("assignmentGroupColIndex", RIGHT_READ_ONLY_COLS);
                jsonAssignmentGroup.put("assignmentGroupName", assignmentGroup.getName());
                jsonAssignmentGroup.put("assignmentGroupWeight", assignmentGroup.getGroupWeight());
                assignmentGroupArray.put(assignmentGroup.getId().toString(), jsonAssignmentGroup);
                tableHeaderList.add(String.format("%s (%s%%)", assignmentGroup.getName(), assignmentGroup.getGroupWeight().toString()));
                assignmentGroupTotalWeight = assignmentGroupTotalWeight.add(new BigDecimal(assignmentGroup.getGroupWeight()));
                RIGHT_READ_ONLY_COLS++;
            }
            model.addAttribute("groupWeightsNot100", assignmentGroupTotalWeight.compareTo(new BigDecimal(100)) != 0);

            tableHeaderList.add(messageSource.getMessage("shared_current_grade", null, LocaleContextHolder.getLocale()));
            tableHeaderList.add(messageSource.getMessage("shared_final_grade", null, LocaleContextHolder.getLocale()));
            RIGHT_READ_ONLY_COLS = RIGHT_READ_ONLY_COLS + 2;

            stopwatch.stop();
            log.debug("Building all the gradebook table took {} for {} assignments.", stopwatch, submissionsMap.size());

            stopwatch.reset();
            stopwatch.start();
            List<List<String>> studentRowList = new ArrayList<>();
            Map<String, List<BigDecimal>> assignmentStatisticsMap = new HashMap<>();

            for (User user : userList) {
                String sisUserId = user.getSisUserId();
                String userId = String.valueOf(user.getId());
                List<String> userGrades = new ArrayList<>();
                //Add the SIS studentId if available.
                userGrades.add(StringUtils.isNotBlank(sisUserId) ? sisUserId : userId);
                userGrades.add(user.getSortableName());
                //Fill the section
                String section = StringUtils.EMPTY;
                if(user.getEnrollments() != null && !user.getEnrollments().isEmpty()) {
                    StringJoiner joiner = new StringJoiner(",");
                    for(Enrollment enrollment : user.getEnrollments()) {
                        joiner.add(sectionMap.get(enrollment.getCourseSectionId()));
                    }
                    section = joiner.toString();
                }
                userGrades.add(section);

                Optional<StudentFinalGrade> studentFinalGradeOverride = gradeService.getStudentOverridedFinalGrade(courseId, userId);
                List<Map<String, Object>> userSettings = new ArrayList<>();
                Map<String, Object> emptyCellSettings = new HashMap<>();
                emptyCellSettings.put("userId", userId);
                emptyCellSettings.put("isFinalGradeOverride", studentFinalGradeOverride.isPresent());

                //Add empty settings for the first three columns, userId, userName and sectionName.
                for(int i = 0 ; i<LEFT_READ_ONLY_COLS; i++) {
                    userSettings.add(emptyCellSettings);
                }

                for (Assignment assignment : assignmentList) {
                    String assignmentId = String.valueOf(assignment.getId());
                    String assignmentGroupId = String.valueOf(assignment.getAssignmentGroupId());
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(userId::equals);
                    boolean assignmentIsMuted = assignmentMutedMap.get(assignment.getId());
                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isZeroPoints = assignment.getPointsPossible() == null || assignment.getPointsPossible().equals(new Double(0));
                    String grade = StringUtils.EMPTY;
                    Map<String, Object> cellSettings = new HashMap<>();
                    boolean gradeTypeNotSupported = false;

                    List<Submission> submissionsForAssignment = submissionsMap.get(assignmentId);
                    if (submissionsForAssignment == null) {
                        submissionsForAssignment = canvasService.getCourseSubmissions(courseId, assignment.getId());
                    }
                    Optional<Submission> optionalGrade = submissionsForAssignment.stream()
                            .filter(submission -> submission.getUserId() != null && userId.equals(submission.getUserId().toString()))
                            .findAny();

                    grade = optionalGrade.isPresent() ? optionalGrade.get().getGrade() : StringUtils.EMPTY;

                    String assignmentConversionScale = coursePreference.getConversionScale();
                    Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                    if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                        assignmentConversionScale = assignmentPreference.get().getConversionScale();
                        cellSettings.put("assignmentConversionScale", assignmentConversionScale);
                    } else {
                        cellSettings.put("assignmentConversionScale", "");
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
                            gradeTypeNotSupported = true;
                            break;
                    }

                    cellSettings.put("gradedPreviously", optionalGrade.isPresent() && StringUtils.isNotBlank(optionalGrade.get().getGrade()) && !(gradeTypeNotSupported || isZeroPoints));

                    //Get the grade from persistence, get the grade from the API otherwise.
                    Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, userId);
                    if (overwrittenStudentGrade.isPresent()) {
                        grade = overwrittenStudentGrade.get().getGrade();
                        cellSettings.put("overwrittenGrade", (overwrittenStudentGrade.isPresent() && StringUtils.isNotBlank(grade)));
                        gradeTypeNotSupported = false;
                    }

                    try {
                        // If grade is less than MIN_GRADE or more than MAX_GRADE
                        cellSettings.put("gradeOutOfRange", (new BigDecimal(grade).compareTo(GradeUtils.MAX_GRADE) > 0) || (new BigDecimal(grade).compareTo(GradeUtils.MIN_GRADE) < 0));
                    } catch (Exception ex) {
                        cellSettings.put("gradeOutOfRange", false);
                    }
                    cellSettings.put("speedGraderUrl", String.format(SPEED_GRADER_URL + "&student_id=%s", canvasBaseUrl, courseId, assignmentId, userId));
                    cellSettings.put("isVisibleForUser", isVisibleForUser);
                    cellSettings.put("assignmentGroupId", assignmentGroupId);
                    cellSettings.put("omitFromFinalGrade", omitFromFinalGrade);
                    cellSettings.put("assignmentIsMuted", assignmentIsMuted);
                    cellSettings.put("isZeroPoints", isZeroPoints);
                    cellSettings.put("assignmentId", assignmentId);
                    cellSettings.put("userId", userId);
                    cellSettings.put("sortableName", user.getSortableName());
                    cellSettings.put("gradeTypeNotSupported", gradeTypeNotSupported);
                    userSettings.add(cellSettings);
                    userGrades.add(StringUtils.isNotBlank(grade) ? grade : GRADE_NOT_AVAILABLE);

                    if (!Arrays.asList(ASSIGNMENT_STATISTIC_INVALID_VALUES).contains(grade)) {
                        List<BigDecimal> assignmentGrades = assignmentStatisticsMap.getOrDefault(assignmentId, new ArrayList<>());
                        assignmentGrades.add(new BigDecimal(grade));
                        assignmentStatisticsMap.put(assignmentId, assignmentGrades);
                    }
                }

                //Add The last columns, assignment groups and totals
                for(int i = 0 ; i < RIGHT_READ_ONLY_COLS; i++) {
                    userSettings.add(emptyCellSettings);
                    userGrades.add(GRADE_NOT_AVAILABLE);
                }

                cellRowListSettings.add(userSettings);
                studentRowList.add(userGrades);
            }

            for (Assignment assignment : assignmentList) {
                AssignmentStatistic assignmentStats = new AssignmentStatistic();
                assignmentStats.setAssignmentId(assignment.getId());
                BigDecimal gradesSum = BigDecimal.ZERO;
                BigDecimal maximumGrade = BigDecimal.ZERO;
                BigDecimal minimumGrade = BigDecimal.TEN;
                List<BigDecimal> assignmentGrades = (ArrayList) assignmentStatisticsMap.getOrDefault(String.valueOf(assignment.getId()), new ArrayList<>());
                if (assignmentGrades.isEmpty()) {
                    assignmentStats.setAverageScore(GRADE_NOT_AVAILABLE);
                    assignmentStats.setHighestGrade(GRADE_NOT_AVAILABLE);
                    assignmentStats.setLowestGrade(GRADE_NOT_AVAILABLE);
                    assignmentStats.setSubmissions(0);

                } else {
                    for (BigDecimal grade : assignmentGrades) {
                        gradesSum = gradesSum.add(grade);
                        if (grade.compareTo(minimumGrade) < 0) {
                            minimumGrade = grade;
                        }
                        if (grade.compareTo(maximumGrade) > 0) {
                            maximumGrade = grade;
                        }
                    }
                    assignmentStats.setAverageScore(gradesSum.divide(new BigDecimal(assignmentGrades.size()), 1, RoundingMode.HALF_UP).toString());
                    assignmentStats.setHighestGrade(maximumGrade.toString());
                    assignmentStats.setLowestGrade(minimumGrade.toString());
                    assignmentStats.setSubmissions(assignmentGrades.size());
                }
                assignmentService.saveAssignmentStatistic(assignmentStats);
            }

            //Model: Data sent to the UI
            model.addAttribute("studentRowList", studentRowList);
            model.addAttribute("cellRowListSettings", cellRowListSettings);
            model.addAttribute("assignmentList", assignmentList);
            model.addAttribute("tableHeaderList", tableHeaderList);
            model.addAttribute("assignmentGroupArray", assignmentGroupArray.toMap());
            model.addAttribute("sectionList", sectionList);
            model.addAttribute("LEFT_READ_ONLY_COLS", LEFT_READ_ONLY_COLS);
            model.addAttribute("RIGHT_READ_ONLY_COLS", RIGHT_READ_ONLY_COLS);
            model.addAttribute("TOTAL_COLUMNS", tableHeaderList.size());
            //Display the admin link if the user is admin
            //The admin button was hidden after the epsilon version, now the administration view is displayed inside the special admin context.
            //model.addAttribute("isAdminUser", securityService.isAdminUser(canvasLoginId) || lld.getRolesList().contains(InstitutionRole.Administrator));
            model.addAttribute("isAdminUser", false);
            //Display the banner options
            boolean bannerEnabled = false;
            Optional<Course> courseOptional = canvasService.getSingleCourse(courseId);
            if (courseOptional.isPresent()) {
                Optional<Account> mainSubAccount = canvasService.getSubAccountForCourseAccount(String.valueOf(courseOptional.get().getAccountId()));
                if (mainSubAccount.isPresent() && !securityService.isTeachingAssistant(lld.getRolesList())) {
                    bannerEnabled = securityService.isBannerEnabled(mainSubAccount.get().getId());
                }
            }
            model.addAttribute("isBannerEnabled", bannerEnabled);
            model.addAttribute("sectionMap", sectionMap);
            //Add the course preferences
            model.addAttribute("coursePreference", coursePreference);
            stopwatch.stop();
            log.debug("fill the instructor view took {} for {} students.", stopwatch, studentRowList.size());
            //Post an event to the tracking service
            eventTrackingService.postEvent(EventConstants.INSTRUCTOR_ACCESS, canvasUserId, courseId);
            return new ModelAndView(TemplateConstants.INSTRUCTOR_TEMPLATE);
        } catch (Exception e) {
            log.error("Fatal error getting the instructor view for the user {} and the course {}.", canvasUserId, courseId, e);
        }

        return new ModelAndView(TemplateConstants.ERROR_TEMPLATE);
    }

    private ModelAndView handleStudentView(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        log.debug("The user {} with id {} is entering the student view.", canvasLoginId, canvasUserId);

        try {
            StopWatch stopwatch = StopWatch.createStarted();
            stopwatch.reset();
            stopwatch.start();
            List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
            stopwatch.stop();
            log.debug("listCourseAssignments took {} for {} assignments.", stopwatch, assignmentList.size());
            
            stopwatch.reset();
            stopwatch.start();
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
            stopwatch.stop();
            log.debug("listAssignmentGroups took {} for {} groups.", stopwatch, assignmentGroupList.size());

            stopwatch.reset();
            stopwatch.start();
            Map<Integer, String> gradeMap = new HashMap<Integer, String>();
            Map<Integer, AssignmentStatistic> assignmentStats = new HashMap<>();
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Assignment assignment : assignmentList) {
                String assignmentId = String.valueOf(assignment.getId());
                Optional<AssignmentPreference> assignmentPref = assignmentService.getAssignmentPreference(assignmentId);
                Optional<AssignmentStatistic> assignmentStat = assignmentService.getAssignmentStatistic(assignmentId);
                if (assignmentStat.isPresent()) assignmentStats.put(assignment.getId(), assignmentStat.get());
                /*boolean assignmentIsMuted = "true".equals(assignment.getMuted());
                boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();*/

                //Get the assigment submissions in a separated thread.
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        String grade = StringUtils.EMPTY;
                        //Get the grade from persistence, get the grade from the API otherwise.
                        Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, canvasUserId);
                        if (overwrittenStudentGrade.isPresent()) {
                            grade = overwrittenStudentGrade.get().getGrade();
                        } else {
                            Optional<Submission> submission = Optional.empty();
                            try {
                                submission = canvasService.getSingleCourseSubmission(courseId, assignment.getId(), canvasUserId);
                            } catch (IOException e) {
                                log.error("Fatal error getting submission for the course {}, assignment {} and student {}.", courseId, assignmentId, canvasUserId);
                            }
                            grade = submission.isPresent() ? submission.get().getGrade() : StringUtils.EMPTY;

                            String assignmentConversionScale = coursePreference.getConversionScale();
                            Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                            if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                                assignmentConversionScale = assignmentPreference.get().getConversionScale();
                            }

                            //Grade conversion logic
                            if(GradeUtils.GRADE_TYPE_POINTS.equals(assignment.getGradingType())) {
                                grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible());
                            } else if(GradeUtils.GRADE_TYPE_PERCENT.equals(assignment.getGradingType())) { 
                                grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                            }
                        }
        
                        if (assignmentPref.isPresent() && assignmentPref.get().getMuted() != null) {
                            assignment.setMuted(assignmentPref.get().getMuted().toString());
                        }

                        String finalGrade = StringUtils.isNotBlank(grade) ? grade : GRADE_NOT_AVAILABLE;
        
                        gradeMap.put(assignment.getId(), finalGrade);
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

            Map<String, String> assignmentGroupNameMap = new HashMap<String, String>();
            for(AssignmentGroup assignmentGroup : assignmentGroupList) {
                //If there is an empty grade, we don't need to calculate the mean of the group.
                //messageSource.getMessage("student_grade_not_available", null, LocaleContextHolder.getLocale())
                String groupName = String.format("%s (%s%%)", assignmentGroup.getName(), assignmentGroup.getGroupWeight());
                assignmentGroupNameMap.put(String.valueOf(assignmentGroup.getId()), groupName);
            }

            //Model: Data sent to the UI
            model.addAttribute("gradeMap", gradeMap);
            model.addAttribute("assignmentStats", assignmentStats);
            model.addAttribute("assignmentList", assignmentList);
            model.addAttribute("assignmentGroupList", assignmentGroupList);
            model.addAttribute("assignmentGroupNameMap", assignmentGroupNameMap);
            model.addAttribute("userId", canvasUserId);
            model.addAttribute("canvasBaseUrl", canvasBaseUrl);
            model.addAttribute("courseId", courseId);
            stopwatch.stop();
            log.debug("Get all the submissions for the student took {} for {} assignments.", stopwatch, gradeMap.size());

            //Post the event
            eventTrackingService.postEvent(EventConstants.STUDENT_ACCESS, canvasUserId, courseId);
            return new ModelAndView(TemplateConstants.STUDENT_TEMPLATE);
         } catch(Exception ex) {
             log.error("Fatal error getting the student view for the user {} and the course {}.", courseId, canvasUserId, ex);
         }

         return new ModelAndView(TemplateConstants.ERROR_TEMPLATE);
    }

    @GetMapping("/" + TemplateConstants.ADMIN_MAIN_TEMPLATE)
    public ModelAndView adminMain(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId, canvasUserId, lld.getRolesList()))) {
            return new ModelAndView(TemplateConstants.FORBIDDEN_TEMPLATE);
        }

        log.debug("The user {} with id {} is entering the main administrator view.", canvasLoginId, canvasUserId);
        //Model: Data sent to the UI
        model.addAttribute("userFullname", ltiSession.getLtiLaunchData().getLisPersonNameFull());
        model.addAttribute("eventCount", eventTrackingService.getEventCount());
        model.addAttribute("gradeCount", gradeService.getGradeCount());
        model.addAttribute("courseCount", courseService.getCourseCount());
        model.addAttribute("gradedUserCount", gradeService.getGradeCount());

        return new ModelAndView(TemplateConstants.ADMIN_MAIN_TEMPLATE);
    }

    @GetMapping("/" + TemplateConstants.ADMIN_EVENTS_TEMPLATE)
    public ModelAndView adminEvents(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId, canvasUserId, lld.getRolesList()))) {
            return new ModelAndView(TemplateConstants.FORBIDDEN_TEMPLATE);
        }

        log.debug("The user {} with id {} is entering the event administrator view.", canvasLoginId, canvasUserId);
        //Model: Data sent to the UI
        model.addAttribute("userFullname", ltiSession.getLtiLaunchData().getLisPersonNameFull());
        model.addAttribute("adminEvents", true);
        model.addAttribute("eventList", (List<Event>) eventTrackingService.getAllEvents());
        eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS, canvasUserId);
        return new ModelAndView(TemplateConstants.ADMIN_EVENTS_TEMPLATE);
    }

    @GetMapping("/" + TemplateConstants.ADMIN_BANNER_TEMPLATE)
    public ModelAndView adminBanner(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId, canvasUserId, lld.getRolesList()))) {
            return new ModelAndView(TemplateConstants.FORBIDDEN_TEMPLATE);
        }

        log.debug("The user {} with id {} is entering the banner administrator view.", canvasLoginId, canvasUserId);

        List<Account> subaccountList = new ArrayList<Account>();
        try {
            subaccountList.addAll(canvasService.getSubaccounts());
        } catch(Exception ex) {
            log.error("Error getting subaccounts from Canvas.", ex);
        }

        Map<Long, String> subAccountNameMap = new HashMap<Long, String>();
        for (Account subaccount : subaccountList) {
            subAccountNameMap.put(Long.valueOf(subaccount.getId()), subaccount.getName());
        }

        model.addAttribute("accountList", subaccountList);
        model.addAttribute("subAccountNameMap", subAccountNameMap);
        model.addAttribute("accountPreferenceList", accountService.getAllAccountPreferences());

        return new ModelAndView(TemplateConstants.ADMIN_BANNER_TEMPLATE);
    }

    @RequestMapping(value = "/" + TemplateConstants.ADMIN_GRADES_COURSES_TEMPLATE)
    public ModelAndView adminGradesCourses(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam(required = false) String selectedCourse, Model model)  {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId, canvasUserId, lld.getRolesList()))) {
            return new ModelAndView(TemplateConstants.FORBIDDEN_TEMPLATE);
        }

        log.debug("The user {} with id {} is entering the grade courses administrator view.", canvasLoginId, canvasUserId);
        try {
            model.addAttribute("courses", courseService.getAllCourses());
            model.addAttribute("adminGradesCourses", true);
            new Long(selectedCourse);
        } catch(Exception ex) {
            return new ModelAndView(TemplateConstants.ADMIN_GRADES_COURSES_TEMPLATE);
        }
        try {
            List<User> userList = canvasService.getUsersInCourse(selectedCourse);

            List<Section> sectionList = canvasService.getSectionsInCourse(selectedCourse);
            Map<String, String> sectionMap = new HashMap<>();
            Map<String, String> userSectionMap = new HashMap<>();
            for(Section section : sectionList) {
                sectionMap.put(String.valueOf(section.getId()), section.getName());
            }
            for (User user : userList) {
                String section = StringUtils.EMPTY;
                if(user.getEnrollments() != null && !user.getEnrollments().isEmpty()) {
                    StringJoiner joiner = new StringJoiner(",");
                    for(Enrollment enrollment : user.getEnrollments()) {
                        joiner.add(sectionMap.get(enrollment.getCourseSectionId()));
                    }
                    section = joiner.toString();
                }
                userSectionMap.put(Integer.toString(user.getId()), section);
            }

            model.addAttribute("selectedIntegerCourse", Integer.valueOf(selectedCourse));
            model.addAttribute("userList", userList);
            model.addAttribute("userSectionMap", userSectionMap);

        } catch (IOException ex) {
            log.error("Cannot get users in course {}", selectedCourse);
            return null;
        }
        return new ModelAndView(TemplateConstants.ADMIN_GRADES_COURSES_TEMPLATE);
    }

    @RequestMapping(value = "/" + TemplateConstants.ADMIN_GRADES_STUDENTS_TEMPLATE)
    public ModelAndView adminGradesStudents(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam(required = false) String selectedCourse, @RequestParam(required = false) String selectedStudent, Model model)  {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId, canvasUserId, lld.getRolesList()))) {
            return new ModelAndView(TemplateConstants.FORBIDDEN_TEMPLATE);
        }

        log.debug("The user {} with id {} is entering the grade students administrator view.", canvasLoginId, canvasUserId);
        try {
            model.addAttribute("courses", courseService.getAllCourses());
            model.addAttribute("adminGradesStudents", true);
            new Long(selectedCourse);

            List<User> userList = canvasService.getUsersInCourse(selectedCourse);
            model.addAttribute("selectedIntegerCourse", Integer.valueOf(selectedCourse));
            model.addAttribute("userList", userList);
            new Long(selectedStudent);

            model.addAttribute("selectedIntegerStudent", Integer.valueOf(selectedStudent));
            Optional<User> user = userList.stream().filter(u -> u.getId() == Integer.valueOf(selectedStudent)).findFirst();
            if (!user.isPresent()) {
                return new ModelAndView(TemplateConstants.ADMIN_GRADES_STUDENTS_TEMPLATE);
            }

            List<Assignment> assignmentList = canvasService.listCourseAssignments(selectedCourse);
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(selectedCourse);
            CoursePreference coursePreference = courseService.getCoursePreference(selectedCourse);

            Map<Integer, String> gradeMap = new HashMap<Integer, String>();
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Assignment assignment : assignmentList) {
                String assignmentId = String.valueOf(assignment.getId());
                Optional<AssignmentPreference> assignmentPref = assignmentService.getAssignmentPreference(assignmentId);
                /*boolean assignmentIsMuted = "true".equals(assignment.getMuted());
                boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();*/

                //Get the assigment submissions in a separated thread.
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        String grade = StringUtils.EMPTY;
                        //Get the grade from persistence, get the grade from the API otherwise.
                        Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, selectedStudent);
                        if (overwrittenStudentGrade.isPresent()) {
                            grade = overwrittenStudentGrade.get().getGrade();
                        } else {
                            Optional<Submission> submission = Optional.empty();
                            try {
                                submission = canvasService.getSingleCourseSubmission(selectedCourse, assignment.getId(), selectedStudent);
                            } catch (IOException e) {
                                log.error("Fatal error getting submission for the course {}, assignment {} and student {}.", selectedCourse, assignmentId, selectedStudent);
                            }
                            grade = submission.isPresent() ? submission.get().getGrade() : StringUtils.EMPTY;

                            String assignmentConversionScale = coursePreference.getConversionScale();
                            Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                            if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                                assignmentConversionScale = assignmentPreference.get().getConversionScale();
                            }

                            //Grade conversion logic
                            if(GradeUtils.GRADE_TYPE_POINTS.equals(assignment.getGradingType())) {
                                grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible());
                            } else if(GradeUtils.GRADE_TYPE_PERCENT.equals(assignment.getGradingType())) { 
                                grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                            }
                        }

                        if (assignmentPref.isPresent() && assignmentPref.get().getMuted() != null) {
                            assignment.setMuted(assignmentPref.get().getMuted().toString());
                        }

                        String finalGrade = StringUtils.isNotBlank(grade) ? grade : GRADE_NOT_AVAILABLE;
        
                        gradeMap.put(assignment.getId(), finalGrade);
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

            Map<String, String> assignmentGroupNameMap = new HashMap<String, String>();
            for(AssignmentGroup assignmentGroup : assignmentGroupList) {
                //If there is an empty grade, we don't need to calculate the mean of the group.
                //messageSource.getMessage("student_grade_not_available", null, LocaleContextHolder.getLocale())
                String groupName = String.format("%s (%s%%)", assignmentGroup.getName(), assignmentGroup.getGroupWeight());
                assignmentGroupNameMap.put(String.valueOf(assignmentGroup.getId()), groupName);
            }

            model.addAttribute("assignmentList", assignmentList);
            model.addAttribute("assignmentGroupList", assignmentGroupList);
            model.addAttribute("gradeMap", gradeMap);
            model.addAttribute("assignmentGroupNameMap", assignmentGroupNameMap);
            
        } catch(Exception ex) {
            //return new ModelAndView(TemplateConstants.ADMIN_GRADES_STUDENTS_TEMPLATE);
        }
        return new ModelAndView(TemplateConstants.ADMIN_GRADES_STUDENTS_TEMPLATE);
    }

    @GetMapping("/" + TemplateConstants.ADMIN_TOKENS_TEMPLATE)
    public ModelAndView adminTokens(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId, canvasUserId, lld.getRolesList()))) {
            return new ModelAndView(TemplateConstants.FORBIDDEN_TEMPLATE);
        }

        log.debug("The user {} with id {} is entering the token administrator view.", canvasLoginId, canvasUserId);
        model.addAttribute("tokenList", tokenService.getAllTokens());
        return new ModelAndView(TemplateConstants.ADMIN_TOKENS_TEMPLATE);
    }

    @GetMapping("/" + TemplateConstants.SEND_TO_BANNER_TEMPLATE)
    public ModelAndView sendToBanner(@RequestParam(required = false) String sectionId, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        if (sectionId == null) sectionId = "all";
        Map<String, String> bannerGrades = new HashMap<String, String>();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String courseId = ltiSession.getCanvasCourseId();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        boolean userIsCourseMainInstructor = false;
        try {
            List<User> courseUsers = canvasService.getUsersInCourse(courseId);
            // We need the SIS ID of the user because Banner use the RUT instead of the login or the ID.
            Optional<User> optionalUser = canvasService.getTeachersInCourse(courseId).stream().filter(user -> user.getId() == Integer.valueOf(canvasUserId)).findFirst();
            if (!optionalUser.isPresent()) {
                log.error("Fatal error locating user sis identifier using canvas id {}.", canvasUserId);
                return new ModelAndView(TemplateConstants.FORBIDDEN_BANNER_TEMPLATE);
            }
            String sisUserId = optionalUser.get().getSisUserId();

            log.debug("The user {} with id {} is entering the banner view.", canvasLoginId, canvasUserId, sisUserId);

            JSONObject userSections = new JSONObject();
            List<Section> sectionList = canvasService.getSectionsInCourse(courseId);
            Map<String, String> sectionMap = new HashMap<>();

            StopWatch stopwatch = StopWatch.createStarted();
            for(Section section : sectionList) {
                if ("all".equals(sectionId) || sectionId.equals(section.getId().toString())) {
                    sectionMap.put(String.valueOf(section.getId()), section.getName());
                    try {
                        String sisSectionId = section.getSisSectionId();
                        log.debug("Getting banner grades for the section {}.", sisSectionId);
                        String[] splittedSectionId = sisSectionId.split("-");
                        String academicPeriod = splittedSectionId[0];
                        String nrcCode = splittedSectionId[1];
                        /*String courseInitials = splittedSectionId[2];
                        String sectionNumber = splittedSectionId[3];*/
                        if(bannerServiceDao.isCourseMainInstructor(nrcCode, academicPeriod, sisUserId)) {
                            bannerGrades.putAll(bannerServiceDao.getBannerUserListFromCourse(nrcCode, academicPeriod, sisUserId));
                            userIsCourseMainInstructor = true;
                        }
                    } catch(Exception e) {
                        log.error("Cannot get the banner grades from the section {}.", section.getSisSectionId());
                    }
                }
            }
            stopwatch.stop();
            log.debug("Got {} grades from Banner, the process took {}.", bannerGrades.size(), stopwatch);

            for (User user : courseUsers) {
                Object section = StringUtils.EMPTY;
                if(user.getEnrollments() != null && !user.getEnrollments().isEmpty()) {
                    StringJoiner joiner = new StringJoiner(",");
                    for(Enrollment enrollment : user.getEnrollments()) {
                        joiner.add(sectionMap.get(enrollment.getCourseSectionId()));
                    }
                    section = joiner.toString();
                }
                userSections.put(Integer.toString(user.getId()), section);
            }

            model.addAttribute("courseUsers", courseUsers);
            model.addAttribute("userSections", userSections.toMap());
            model.addAttribute("bannerGrades", bannerGrades);
            model.addAttribute("bannerEventList", eventTrackingService.getAllEventsByEventCourseAndEventTypes(courseId, Arrays.asList(EventConstants.BANNER_SEND_GRADE_SUCCESS)));
            model.addAttribute("bannerErrorEventList", eventTrackingService.getAllEventsByEventCourseAndEventTypes(courseId, Arrays.asList(EventConstants.BANNER_SEND_GRADE_FAIL)));
            model.addAttribute("userIsCourseMainInstructor", userIsCourseMainInstructor);
            model.addAttribute("sectionId", sectionId);

            return new ModelAndView(TemplateConstants.SEND_TO_BANNER_TEMPLATE);

        } catch (IOException ex) {
            log.error("Cannot get users in course {}", courseId);
            return null;
        }
    }

    private boolean checkAdminPermissions(String canvasLoginId, String canvasUserId, List<InstitutionRole> userRoles) {
        if (!securityService.isAdminUser(canvasLoginId, userRoles)) {
            log.info("User {} tried to access the admin area without proper permissions.", canvasLoginId);
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId);
            return false;
        }
        return true;
    }

}

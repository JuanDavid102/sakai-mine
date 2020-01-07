package edu.uc.ltigradebook.controller;

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

import edu.uc.ltigradebook.constants.EventConstant;
import edu.uc.ltigradebook.dao.BannerServiceDao;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.Event;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.service.AssignmentService;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.CourseService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.GradeService;
import edu.uc.ltigradebook.service.SecurityService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    @Value("${lti-gradebook.url:someurl}")
    private String canvasBaseUrl;

    private static final String GRADE_NOT_AVAILABLE = "-";
    private static final String SPEED_GRADER_URL = "%s/courses/%s/gradebook/speed_grader?assignment_id=%s";

    @GetMapping("/")
    public ModelAndView index(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));
        return new ModelAndView("index");
    }

    @GetMapping("/index")
    public ModelAndView index(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model, @RequestParam(required=false) Boolean errors) {
        try {
            LtiLaunchData lld = ltiSession.getLtiLaunchData();
            log.info("Debugging ltiLaunch Object:\n"+ReflectionToStringBuilder.toString(lld));
            String canvasLoginId = ltiPrincipal.getUser();
            String courseId = ltiSession.getCanvasCourseId();
            model.addAttribute("courseId", courseId);
            model.addAttribute("errors", errors);

            eventTrackingService.postEvent(EventConstant.LOGIN, canvasLoginId, courseId);
            
            if(lld.getRolesList() == null || lld.getRolesList().isEmpty()) {
                throw new Exception(String.format("The user %s doesn't have any valid role.", canvasLoginId));       
            }
            
            if(securityService.isStudent(lld.getRolesList())) {
                return handleStudentView(ltiPrincipal, ltiSession, model);
            }

            if (securityService.isFaculty(lld.getRolesList())) {
                return handleInstructorView(ltiPrincipal, ltiSession, model);
            }

        } catch(Exception ex) {
            log.error("Error displaying the LTI tool content.", ex);
        }

        return new ModelAndView("error");
    }

    private ModelAndView handleInstructorView(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        log.info("Entering the instructor view...");
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get("canvas_user_id");
        String canvasLoginId = ltiPrincipal.getUser();
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);

        try {
            StopWatch stopwatch = StopWatch.createStarted();
            List<User> userList = canvasService.getUsersInCourse(courseId);
            stopwatch.stop();
            log.info("getUsersInCourse took {} for {} users.", stopwatch, userList.size());
            
            stopwatch.reset();
            stopwatch.start();
            List<Section> sectionList = canvasService.getSectionsInCourse(courseId);
            Map<String, String> sectionMap = new HashMap<String, String>();
            for(Section section : sectionList) {
                sectionMap.put(String.valueOf(section.getId()), section.getName());
            }
            stopwatch.stop();
            log.info("getSectionsInCourse took {} for {} sections.", stopwatch, sectionList.size());

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
            log.info("getSingleCourse took {}.", stopwatch);

            stopwatch.reset();
            stopwatch.start();
            List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
            stopwatch.stop();
            log.info("listCourseAssignments took {} for {} assignments.", stopwatch, assignmentList.size());
            
            stopwatch.reset();
            stopwatch.start();
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
            stopwatch.stop();
            log.info("listAssignmentGroups took {} for {} groups.", stopwatch, assignmentGroupList.size());

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
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Assignment assignment : assignmentList) {
                Map<String, Object> cellSettings = new HashMap<>();
                String assignmentId = String.valueOf(assignment.getId());
                tableHeaderList.add(assignment.getName());
                boolean assignmentIsMuted = "true".equals(assignment.getMuted());
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
            model.addAttribute("groupWeightsNot100", !assignmentGroupTotalWeight.equals(new BigDecimal(100)));

            tableHeaderList.add(messageSource.getMessage("shared_current_grade", null, LocaleContextHolder.getLocale()));
            tableHeaderList.add(messageSource.getMessage("shared_final_grade", null, LocaleContextHolder.getLocale()));
            RIGHT_READ_ONLY_COLS = RIGHT_READ_ONLY_COLS + 2;

            stopwatch.stop();
            log.info("Building all the gradebook table took {} for {} assignments.", stopwatch, submissionsMap.size());

            stopwatch.reset();
            stopwatch.start();
            List<List<String>> studentRowList = new ArrayList<>();

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

                List<Map<String, Object>> userSettings = new ArrayList<>();
                Map<String, Object> emptyCellSettings = new HashMap<>();
                emptyCellSettings.put("userId", userId);
                //Add empty settings for the first three columns, userId, userName and sectionName.
                for(int i = 0 ; i<LEFT_READ_ONLY_COLS; i++) {
                    userSettings.add(emptyCellSettings);
                }

                for (Assignment assignment : assignmentList) {
                    String assignmentId = String.valueOf(assignment.getId());
                    String assignmentGroupId = String.valueOf(assignment.getAssignmentGroupId());
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(userId::equals);
                    boolean assignmentIsMuted = "true".equals(assignment.getMuted());
                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isZeroPoints = assignment.getPointsPossible() == null || assignment.getPointsPossible().equals(new Double(0));
                    String grade = StringUtils.EMPTY;
                    Map<String, Object> cellSettings = new HashMap<>();
                    boolean gradeTypeNotSupported = false;
                    boolean noPointsPossible = false;

                    List<Submission> submissionsForAssignment = submissionsMap.get(assignmentId);
                    if (submissionsForAssignment == null) {
                        submissionsForAssignment = canvasService.getCourseSubmissions(courseId, assignment.getId());
                    }
                    Optional<Submission> optionalGrade = submissionsForAssignment.stream()
                            .filter(submission -> submission.getUserId() != null && userId.equals(submission.getUserId().toString()))
                            .findAny();

                    grade = optionalGrade.isPresent() ? optionalGrade.get().getGrade() : StringUtils.EMPTY;

                    //Display an alert if the grade is not mappable
                    if (assignment.getPointsPossible() == null || new Double("0").compareTo(assignment.getPointsPossible()) == 0) {
                        noPointsPossible = true;
                    }

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
                            grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible().toString());
                            break;
                        case GradeUtils.GRADE_TYPE_PERCENT:
                            grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                            break;
                        default:
                            grade = GRADE_NOT_AVAILABLE;
                            gradeTypeNotSupported = true;
                            break;
                    }

                    cellSettings.put("gradedPreviously", optionalGrade.isPresent() && StringUtils.isNotBlank(optionalGrade.get().getGrade()) && !(gradeTypeNotSupported || noPointsPossible));

                    //Get the grade from persistence, get the grade from the API otherwise.
                    Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, userId);
                    if (overwrittenStudentGrade.isPresent()) {
                        grade = overwrittenStudentGrade.get().getGrade();
                        cellSettings.put("overwrittenGrade", (overwrittenStudentGrade.isPresent() && StringUtils.isNotBlank(grade)));
                        gradeTypeNotSupported = false;
                    }

                    cellSettings.put("isVisibleForUser", isVisibleForUser);
                    cellSettings.put("assignmentGroupId", assignmentGroupId);
                    cellSettings.put("omitFromFinalGrade", omitFromFinalGrade);
                    cellSettings.put("assignmentIsMuted", assignmentIsMuted);
                    cellSettings.put("isZeroPoints", isZeroPoints);
                    cellSettings.put("assignmentId", assignmentId);
                    cellSettings.put("userId", userId);
                    cellSettings.put("gradeTypeNotSupported", gradeTypeNotSupported);
                    cellSettings.put("noPointsPossible", noPointsPossible);
                    userSettings.add(cellSettings);
                    userGrades.add(StringUtils.isNotBlank(grade) ? grade : GRADE_NOT_AVAILABLE);
                }

                //Add The last columns, assignment groups and totals
                for(int i = 0 ; i < RIGHT_READ_ONLY_COLS; i++) {
                    userSettings.add(emptyCellSettings);
                    userGrades.add(GRADE_NOT_AVAILABLE);
                }

                cellRowListSettings.add(userSettings);
                studentRowList.add(userGrades);
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
            model.addAttribute("isAdminUser", securityService.isAdminUser(canvasLoginId) || lld.getRolesList().contains(InstitutionRole.Administrator));
            //Display the banner options
            model.addAttribute("isBannerEnabled", securityService.isBannerEnabled(canvasService.getSingleCourse(courseId).get().getAccountId()));
            //Add the course preferences
            model.addAttribute("coursePreference", coursePreference);
            stopwatch.stop();
            log.info("fill the instructor view took {} for {} students.", stopwatch, studentRowList.size());
            //Post an event to the tracking service
            eventTrackingService.postEvent(EventConstant.INSTRUCTOR_VIEW, canvasLoginId, courseId);
            return new ModelAndView("instructor");
        } catch (Exception e) {
            log.error("Fatal error getting the instructor view for the user {} and the course {}.", canvasUserId, courseId, e);
        }

        return new ModelAndView("error");
    }

    private ModelAndView handleStudentView(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        log.info("Entering the student view...");
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get("canvas_user_id");
        String canvasLoginId = ltiPrincipal.getUser();
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);

        try {
            StopWatch stopwatch = StopWatch.createStarted();
            stopwatch.reset();
            stopwatch.start();
            List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
            stopwatch.stop();
            log.info("listCourseAssignments took {} for {} assignments.", stopwatch, assignmentList.size());
            
            stopwatch.reset();
            stopwatch.start();
            List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
            stopwatch.stop();
            log.info("listAssignmentGroups took {} for {} groups.", stopwatch, assignmentGroupList.size());

            stopwatch.reset();
            stopwatch.start();
            Map<Integer, String> gradeMap = new HashMap<Integer, String>();
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Assignment assignment : assignmentList) {
                String assignmentId = String.valueOf(assignment.getId());
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
                                grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible().toString());
                            } else if(GradeUtils.GRADE_TYPE_PERCENT.equals(assignment.getGradingType())) { 
                                grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                            }
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
            model.addAttribute("assignmentList", assignmentList);
            model.addAttribute("assignmentGroupList", assignmentGroupList);
            model.addAttribute("assignmentGroupNameMap", assignmentGroupNameMap);
            model.addAttribute("userId", canvasUserId);
            stopwatch.stop();
            log.info("Get all the submissions for the student took {} for {} assignments.", stopwatch, gradeMap.size());

            //Post the event
            eventTrackingService.postEvent(EventConstant.STUDENT_VIEW, canvasLoginId, courseId);
            return new ModelAndView("student");
         } catch(Exception ex) {
             log.error("Fatal error getting the student view for the user {} and the course {}.", courseId, canvasUserId, ex);
         }

         return new ModelAndView("error");
    }

    @GetMapping("/admin_main")
    public ModelAndView adminMain(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));

        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId) || lld.getRolesList().contains(InstitutionRole.Administrator))) {
            return new ModelAndView("error");
        }
        //Model: Data sent to the UI
        model.addAttribute("userFullname", ltiSession.getLtiLaunchData().getLisPersonNameFull());
        model.addAttribute("adminMain", true);
        model.addAttribute("eventCount", eventTrackingService.getEventCount());
        model.addAttribute("gradeCount", gradeService.getGradeCount());
        model.addAttribute("courseCount", courseService.getCourseCount());
        model.addAttribute("gradedUserCount", gradeService.getGradeCount());

        return new ModelAndView("admin_main");
    }

    @GetMapping("/admin_events")
    public ModelAndView adminEvents(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));

        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId) || lld.getRolesList().contains(InstitutionRole.Administrator))) {
            return new ModelAndView("error");
        }
        //Model: Data sent to the UI
        model.addAttribute("userFullname", ltiSession.getLtiLaunchData().getLisPersonNameFull());
        model.addAttribute("adminEvents", true);
        model.addAttribute("eventList", (List<Event>) eventTrackingService.getAllEvents());
        eventTrackingService.postEvent(EventConstant.ADMIN_VIEW, canvasLoginId);
        return new ModelAndView("admin_events");
    }

    @GetMapping("/admin_banner")
    public ModelAndView adminBanner(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        localeResolver.setDefaultLocale(new Locale(lld.getLaunchPresentationLocale()));

        String canvasLoginId = ltiPrincipal.getUser();
        if (!(checkAdminPermissions(canvasLoginId) || lld.getRolesList().contains(InstitutionRole.Administrator))) {
            return new ModelAndView("error");
        }
        model.addAttribute("userFullname", ltiSession.getLtiLaunchData().getLisPersonNameFull());
        model.addAttribute("adminBanner", true);
        try {
            model.addAttribute("accountList", canvasService.getSubaccounts());
        } catch(Exception ex) {
            log.error("Error getting subaccounts from Canvas.", ex);
        }
        return new ModelAndView("admin_banner");
    }

    @GetMapping("/send_to_banner")
    public ModelAndView sendToBanner(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, Model model) {
        Map<String, String> bannerGrades = new HashMap<String, String>();
        String courseId = ltiSession.getCanvasCourseId();
        String canvasLoginId = ltiPrincipal.getUser();
        boolean userIsCourseMainInstructor = false;
        try {
            List<User> courseUsers = canvasService.getUsersInCourse(courseId);

            JSONObject userSections = new JSONObject();
            List<Section> sectionList = canvasService.getSectionsInCourse(courseId);
            Map<String, String> sectionMap = new HashMap<>();

            StopWatch stopwatch = StopWatch.createStarted();
            for(Section section : sectionList) {
                sectionMap.put(String.valueOf(section.getId()), section.getName());
                try {
                    String sisSectionId = section.getSisSectionId();
                    log.info("Getting banner grades for the section {}.", sisSectionId);
                    String[] splittedSectionId = sisSectionId.split("-");
                    String academicPeriod = splittedSectionId[0];
                    String nrcCode = splittedSectionId[1];
                    /*String courseInitials = splittedSectionId[2];
                    String sectionNumber = splittedSectionId[3];*/
                    userIsCourseMainInstructor = bannerServiceDao.isCourseMainInstructor(nrcCode, academicPeriod, canvasLoginId);
                    if(userIsCourseMainInstructor) {
                        bannerGrades.putAll(bannerServiceDao.getBannerUserListFromCourse(nrcCode, academicPeriod, canvasLoginId));
                    }
                } catch(Exception e) {
                    log.error("Cannot get the banner grades from the section {}.", section.getSisSectionId());
                }
            }
            stopwatch.stop();
            log.info("Got {} grades from Banner, the process took {}.", bannerGrades.size(), stopwatch);

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
            model.addAttribute("userIsCourseMainInstructor", userIsCourseMainInstructor);

            return new ModelAndView("send_to_banner");

        } catch (IOException ex) {
            log.error("Cannot get users in course {}", courseId);
            return null;
        }
    }

    private boolean checkAdminPermissions(String canvasLoginId) {
        if (!securityService.isAdminUser(canvasLoginId)) {
            log.info("User {} tried to access the admin area without proper permissions.", canvasLoginId);
            eventTrackingService.postEvent(EventConstant.ADMIN_FORBIDDEN, canvasLoginId);
            return false;
        }
        return true;
    }

}

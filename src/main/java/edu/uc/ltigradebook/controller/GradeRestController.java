package edu.uc.ltigradebook.controller;

import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstant;
import edu.uc.ltigradebook.dao.BannerServiceDao;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.service.AssignmentService;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.CourseService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.GradeService;
import edu.uc.ltigradebook.service.SecurityService;
import edu.uc.ltigradebook.util.GradeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
public class GradeRestController {
    
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
    private AssignmentService assignmentService;

    @Autowired
    private SecurityService securityService;

    private static final String GRADE_NOT_AVAILABLE = "-";

    @RequestMapping(value = "/postGrade", method = RequestMethod.POST)
    public boolean postGrade(@RequestBody StudentGrade studentGrade, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException {
        String courseId = ltiSession.getCanvasCourseId();
        String gradeString = StringUtils.replace(studentGrade.getGrade(), ",", ".");
        
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        if (securityService.isFaculty(lld.getRolesList())) {
            if(StringUtils.isNotBlank(gradeString) && !GradeUtils.isValidGrade(gradeString)) {
                log.warn("The grade {} is not valid, it will not be saved", gradeString);
                throw new GradeException();
            }

            //Set one decimal
            gradeString = GradeUtils.roundGrade(gradeString);

            studentGrade.setGrade(gradeString);
            String eventDetails = new JSONObject().put("assignmentId", studentGrade.getAssignmentId()).put("userId", studentGrade.getUserId()).put("grade", gradeString).toString();
            log.debug("Posting grade {} for the user {} in the assignment {} and the course {}.", gradeString, studentGrade.getUserId(), studentGrade.getAssignmentId(), courseId);
            if (StringUtils.isBlank(gradeString)) {
                log.debug("The inserted grade is empty, deleting grade...");
                eventTrackingService.postEvent(EventConstant.INSTRUCTOR_DELETE_GRADE, ltiPrincipal.getUser(), courseId, eventDetails);
                gradeService.deleteGrade(studentGrade);
                return true;
            } else {
                eventTrackingService.postEvent(EventConstant.INSTRUCTOR_POST_GRADE, ltiPrincipal.getUser(), courseId, eventDetails);
                gradeService.saveGrade(studentGrade);
                return true;
            }

        } else {
            log.warn("User role is not valid");
            throw new GradeException();
        }
    }

    @RequestMapping(value = "/getStudentGroupGrade", method = { RequestMethod.GET, RequestMethod.POST })
    public String getStudentGroupMean(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam Long groupId, @RequestParam Integer studentId) throws GradeException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get("canvas_user_id");
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        if (canvasUserId.equals(studentId.toString()) || securityService.isFaculty(lld.getRolesList())) {
            BigDecimal groupMeanSum = BigDecimal.ZERO;
            BigDecimal gradesLength = BigDecimal.ZERO;
            boolean calculateFinalGrade = true;

            try {
                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
                for (Assignment assignment : assignmentList) {
                    boolean assignmentIsMuted = "true".equals(assignment.getMuted());
                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isZeroPoints = assignment.getPointsPossible() == null || assignment.getPointsPossible().equals(new Double(0));
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(studentId.toString()::equals);

                    // Skip if assignment is not in the group, assignment is muted, grade is omitted from final grade or assignment possible points is zero
                    if (!assignment.getAssignmentGroupId().equals(groupId) || assignmentIsMuted || omitFromFinalGrade || isZeroPoints || !isVisibleForUser) continue;

                    List<Submission> assignmentSubmissions = canvasService.getCourseSubmissions(courseId, assignment.getId());
                    for (Submission submission : assignmentSubmissions) {
                        // Skip if is not submitted by the requested student
                        if (!submission.getUserId().equals(studentId)) continue;

                        String assignmentId = String.valueOf(assignment.getId());
                        String grade;
                        boolean gradeTypeNotSupported = false;
                        boolean noPointsPossible = false;

                        grade = submission.getGrade();

                        //Display an alert if the grade is not mappable
                        if (assignment.getPointsPossible() == null || new Double("0").compareTo(assignment.getPointsPossible()) == 0) {
                            noPointsPossible = true;
                        }

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
                                gradeTypeNotSupported = true;
                                break;
                        }

                        //Get the grade from persistence, get the grade from the API otherwise.
                        Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, String.valueOf(studentId));
                        if (overwrittenStudentGrade.isPresent()) {
                            grade = overwrittenStudentGrade.get().getGrade();
                            gradeTypeNotSupported = false;
                        }

                        if (!gradeTypeNotSupported) {
                            if (StringUtils.isBlank(grade)) {
                                calculateFinalGrade = false;
                                break;
                            } else {
                                groupMeanSum = groupMeanSum.add(new BigDecimal(grade));
                                gradesLength = gradesLength.add(BigDecimal.ONE);
                            }
                        }
                    }
                }

            } catch (IOException ex) {
                log.error("Error getting student {} group {} mean", studentId, groupId);
            }

            if (calculateFinalGrade && !gradesLength.equals(BigDecimal.ZERO)) {
                BigDecimal groupMean = groupMeanSum.divide(gradesLength, 3, RoundingMode.HALF_UP);
                return new BigDecimal(GradeUtils.roundGrade(groupMean.toString())).toString();
            } else return GRADE_NOT_AVAILABLE;

        } else {
            log.error("This user is not allowed to see student {} group {} mean", studentId, groupId);
            throw new GradeException();
        }
    }

    @RequestMapping(value = "/getStudentFinalGrade", method = RequestMethod.POST)
    public String getStudentTotalMean(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam Integer studentId, @RequestParam boolean isCurrentGrade) throws GradeException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get("canvas_user_id");
        String courseId = ltiSession.getCanvasCourseId();
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        if (canvasUserId.equals(studentId.toString()) || securityService.isFaculty(lld.getRolesList())) {
            boolean calculateFinalGrade = true;
            Map<Long, List<BigDecimal>> groupGrades = new HashMap<Long, List<BigDecimal>>();

            try {

                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
                for (Assignment assignment : assignmentList) {
                    boolean assignmentIsMuted = "true".equals(assignment.getMuted());
                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isZeroPoints = assignment.getPointsPossible() == null || assignment.getPointsPossible().equals(new Double(0));
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(studentId.toString()::equals);

                    // Skip if assignment is not in the group, assignment is muted, grade is omitted from final grade or assignment possible points is zero
                    if (assignmentIsMuted || omitFromFinalGrade || isZeroPoints || !isVisibleForUser) continue;

                    List<Submission> assignmentSubmissions = canvasService.getCourseSubmissions(courseId, assignment.getId());
                    for (Submission submission : assignmentSubmissions) {
                        // Skip if is not submitted by the requested student
                        if (!submission.getUserId().equals(studentId)) continue;

                        String assignmentId = String.valueOf(assignment.getId());
                        String grade;
                        boolean gradeTypeNotSupported = false;
                        boolean noPointsPossible = false;

                        grade = submission.getGrade();

                        //Display an alert if the grade is not mappable
                        if (assignment.getPointsPossible() == null || new Double("0").compareTo(assignment.getPointsPossible()) == 0) {
                            noPointsPossible = true;
                        }

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
                                gradeTypeNotSupported = true;
                                break;
                        }

                        //Get the grade from persistence, get the grade from the API otherwise.
                        Optional<StudentGrade> overwrittenStudentGrade = gradeService.getGradeByAssignmentAndUser(assignmentId, String.valueOf(studentId));
                        if (overwrittenStudentGrade.isPresent()) {
                            grade = overwrittenStudentGrade.get().getGrade();
                            gradeTypeNotSupported = false;
                        }

                        if (!gradeTypeNotSupported) {
                            if (StringUtils.isBlank(grade)) {
                                if (!isCurrentGrade) calculateFinalGrade = false;
                                break;
                            } else {
                                List<BigDecimal> values = groupGrades.getOrDefault(assignment.getAssignmentGroupId(), new ArrayList<>());
                                values.add(new BigDecimal(grade));
                                groupGrades.put(assignment.getAssignmentGroupId(), values);
                            }
                        }
                    }
                }

                BigDecimal finalValue = BigDecimal.ZERO;
                if (calculateFinalGrade) {
                    BigDecimal assignmentWeightSum = BigDecimal.ZERO;
                    List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
                    for (AssignmentGroup assignmentGroup : assignmentGroupList) {
                        List<BigDecimal> values = groupGrades.get(new Long(assignmentGroup.getId()));
                        
                        if (values != null && !values.isEmpty()) {
                            BigDecimal totalValues = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal totalMean = totalValues.divide(new BigDecimal(values.size()), 3, RoundingMode.HALF_UP);
                            BigDecimal value = totalMean.multiply(new BigDecimal(assignmentGroup.getGroupWeight() / 100));
                            finalValue = finalValue.add(value);
                            assignmentWeightSum = assignmentWeightSum.add(new BigDecimal(assignmentGroup.getGroupWeight()));
                        }
                    }
                    if (assignmentWeightSum.equals(BigDecimal.ZERO)) return GRADE_NOT_AVAILABLE;
                    return new BigDecimal(GradeUtils.roundGrade(finalValue.multiply(new BigDecimal(100)).divide(assignmentWeightSum, 3, RoundingMode.HALF_UP).toString())).toString();
                } else {
                    return GRADE_NOT_AVAILABLE;
                }

            } catch (IOException ex) {
                log.error("Error getting student {} total mean", studentId);
                return GRADE_NOT_AVAILABLE;
            }

        } else {
            log.error("This user is not allowed to see the total mean of the student with id {}", studentId);
            throw new GradeException();
        }
    }

    @RequestMapping(value = "/sendGradesToBanner", method = RequestMethod.POST)
    public boolean sendGradesToBanner(@RequestBody List<StudentGrade> studentGrades, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException, IOException {
        String instructorRUT = ltiPrincipal.getUser();
        String courseId = ltiSession.getCanvasCourseId();

        List<User> userList = canvasService.getUsersInCourse(courseId);
        //Associate the users with the sections
        Map<String, String> studentSectionMap = new HashMap<String, String>();
        for(User user : userList) {
            String sectionId = StringUtils.EMPTY;
            if (!user.getEnrollments().isEmpty()) {
                sectionId = user.getEnrollments().get(0).getSisSectionId();
            }
            studentSectionMap.put(user.getSisUserId(), sectionId);
        }

        for (StudentGrade studentGrade : studentGrades) {
            String userId = studentGrade.getUserId();
            String grade = studentGrade.getGrade();
            //Here the user grade is sent to Banner
            log.info("Send user {} grade {} to Banner", userId, grade);
            try {
                String sectionId = studentSectionMap.get(userId);
                if(StringUtils.isEmpty(sectionId)) {
                    throw new Exception("The section for the user doesn't exist.");
                }
                String[] splittedSectionId = sectionId.split("-");
                String academicPeriod = splittedSectionId[0];
                String nrcCode = splittedSectionId[1];
                /*String courseInitials = splittedSectionId[2];
                String sectionNumber = splittedSectionId[3];*/                    
                bannerServiceDao.sendGradeToBanner(nrcCode, grade, userId, instructorRUT, academicPeriod);
            } catch(Exception e) {
                log.error("Cannot send the grade {} to the student {}.", userId, grade, e);
            }
        }
        return true;
    }
}

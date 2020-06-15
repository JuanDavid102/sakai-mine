package edu.uc.ltigradebook.controller;

import edu.ksu.canvas.model.User;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.dao.BannerServiceDao;
import edu.uc.ltigradebook.entity.StudentFinalGrade;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.GradeService;
import edu.uc.ltigradebook.service.SecurityService;
import edu.uc.ltigradebook.util.GradeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
    private EventTrackingService eventTrackingService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private SecurityService securityService;

    private static final String[] FINAL_GRADE_VALID_VALUES = new String[]{"P", "I", "R", "A"};

    @RequestMapping(value = "/postGrade", method = RequestMethod.POST)
    public boolean postGrade(@RequestBody StudentGrade studentGrade, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String gradeString = StringUtils.replace(studentGrade.getGrade(), ",", ".");
        String eventDetails = new JSONObject().put("assignmentId", studentGrade.getAssignmentId()).put("userId", studentGrade.getUserId()).put("grade", gradeString).put("oldGrade", studentGrade.getOldGrade()).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying post a grade, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            throw new GradeException();
        }

        if(StringUtils.isNotBlank(gradeString) && !GradeUtils.isValidGrade(gradeString)) {
            log.warn("The grade {} is not valid, it will not be saved", gradeString);
            throw new GradeException();
        }

        //Set one decimal
        gradeString = GradeUtils.roundGrade(gradeString);
        studentGrade.setGrade(gradeString);
        log.debug("Posting grade {} for the user {} in the assignment {} and the course {}.", gradeString, studentGrade.getUserId(), studentGrade.getAssignmentId(), courseId);
        if (StringUtils.isBlank(gradeString)) {
            log.debug("The inserted grade is empty, deleting grade...");
            eventTrackingService.postEvent(EventConstants.INSTRUCTOR_DELETE_GRADE, canvasUserId, courseId, eventDetails);
            gradeService.deleteGrade(studentGrade);
            return true;
        } else {
            eventTrackingService.postEvent(EventConstants.INSTRUCTOR_POST_GRADE, canvasUserId, courseId, eventDetails);
            gradeService.saveGrade(studentGrade);
            return true;
        }
    }

    @RequestMapping(value = "/postFinalGrade", method = RequestMethod.POST)
    public boolean postFinalGrade(@RequestBody StudentFinalGrade studentFinalGrade, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException {
        String courseId = studentFinalGrade.getCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String gradeString = StringUtils.replace(studentFinalGrade.getGrade(), ",", ".");
        String eventDetails = new JSONObject().put("assignmentId", courseId).put("userId", studentFinalGrade.getUserId()).put("grade", gradeString).put("oldGrade", studentFinalGrade.getOldGrade()).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying post a grade, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            throw new GradeException();
        }

        if(StringUtils.isNotBlank(gradeString) && !Arrays.asList(FINAL_GRADE_VALID_VALUES).contains(gradeString)) {
            log.warn("The grade {} is not valid, it will not be saved", gradeString);
            throw new GradeException();
        }

        studentFinalGrade.setGrade(gradeString);
        log.debug("Posting grade {} for the user {} in the course {}.", gradeString, studentFinalGrade.getUserId(), courseId);
        if (StringUtils.isBlank(gradeString)) {
            log.debug("The inserted grade is empty, deleting grade...");
            eventTrackingService.postEvent(EventConstants.INSTRUCTOR_DELETE_GRADE, canvasUserId, courseId, eventDetails);
            gradeService.deleteFinalGrade(studentFinalGrade);
            return true;
        } else {
            eventTrackingService.postEvent(EventConstants.INSTRUCTOR_POST_GRADE, canvasUserId, courseId, eventDetails);
            gradeService.saveFinalGrade(studentFinalGrade);
            return true;
        }
    }

    @RequestMapping(value = "/getStudentGroupGrade", method = RequestMethod.POST)
    public Map<String, Object> getStudentGroupMean(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam Long groupId, @RequestParam Integer studentId, @RequestParam(required = false) String courseId) throws GradeException {
        return gradeService.getStudentGroupMean(ltiSession, groupId, studentId, courseId).toMap();
    }

    @RequestMapping(value = "/getStudentFinalGrade", method = RequestMethod.POST)
    public Map<String, Object> getStudentTotalMean(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam Integer studentId, @RequestParam boolean isCurrentGrade, @RequestParam(required = false) String courseId) throws GradeException {
        return gradeService.getStudentTotalMean(ltiSession, studentId, isCurrentGrade, courseId).toMap();
    }

    @RequestMapping(value = "/sendGradesToBanner", method = RequestMethod.POST)
    public boolean sendGradesToBanner(@RequestBody List<StudentGrade> studentGrades, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException, IOException {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying to send the grades to banner, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            throw new GradeException();
        }

        // We need the SIS ID of the user because Banner use the RUT instead of the login or the ID.
        Optional<User> optionalUser = canvasService.getTeachersInCourse(courseId).stream().filter(user -> user.getId() == Integer.valueOf(canvasUserId)).findFirst();
        if (!optionalUser.isPresent()) {
            log.error("Fatal error locating user sis identifier using canvas id {}.", canvasUserId);
            throw new GradeException();
        }
        String sisUserId = optionalUser.get().getSisUserId();
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
            log.debug("Send user {} grade {} to Banner", userId, grade);
            // Send an empty grade to banner, this will wipe the value in the banner side.
            if(StringUtils.isBlank(grade) || "-".equals(grade)) {
                grade = StringUtils.EMPTY;
            }

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
                String errorCode = bannerServiceDao.sendGradeToBanner(nrcCode, grade, userId, sisUserId, academicPeriod);
                JSONObject detailsObject = new JSONObject().put("nrcCode", nrcCode).put("grade", grade).put("userId", userId).put("sisUserId", sisUserId).put("academicPeriod", academicPeriod);
                if (StringUtils.isNotBlank(errorCode)) {
                    detailsObject = detailsObject.put("errorCode", errorCode);
                    eventTrackingService.postEvent(EventConstants.BANNER_SEND_GRADE_FAIL, canvasUserId, courseId, detailsObject.toString());
                } else {
                    eventTrackingService.postEvent(EventConstants.BANNER_SEND_GRADE_SUCCESS, canvasUserId, courseId, detailsObject.toString());
                }
                
            } catch(Exception e) {
                log.error("Cannot send the grade {} to the student {}.", userId, grade, e);
            }
        }

        return true;
    }

    @RequestMapping(value = "/sendMessageToUsers", method = RequestMethod.POST)
    public boolean sendMessageToUsers(@RequestBody String jsonData, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).put("jsonData", jsonData).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying to send messages users, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            throw new GradeException();
        }

        try {
            JSONObject jsonObj = new JSONObject(jsonData);
            JSONArray userIdsArr = jsonObj.getJSONArray("userIds");
            List<String> userIds = new ArrayList<>();
            String subject = jsonObj.getString("sendMessageSubject");
            String message = jsonObj.getString("sendMessageTextarea");
            for (int i = 0; i < userIdsArr.length(); i++) {
                userIds.add(Integer.toString(userIdsArr.getInt(i)));
            }

            canvasService.createConversation(userIds, subject, message);

            // Post an event
            eventTrackingService.postEvent(EventConstants.INSTRUCTOR_SEND_MESSAGE, canvasUserId, courseId, eventDetails);

        } catch (IOException ex) {
            log.warn("Conversation cannot be created");
            throw new GradeException();
        }

        return true;
    }

}

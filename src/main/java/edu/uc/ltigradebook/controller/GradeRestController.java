package edu.uc.ltigradebook.controller;

import edu.ksu.canvas.model.User;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstant;
import edu.uc.ltigradebook.dao.BannerServiceDao;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @RequestMapping(value = "/getStudentGroupGrade", method = RequestMethod.POST)
    public String getStudentGroupMean(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam Long groupId, @RequestParam Integer studentId, @RequestParam(required = false) String courseId) throws GradeException {
        return gradeService.getStudentGroupMean(ltiSession, groupId, studentId, courseId);
    }

    @RequestMapping(value = "/getStudentFinalGrade", method = RequestMethod.POST)
    public String getStudentTotalMean(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession, @RequestParam Integer studentId, @RequestParam boolean isCurrentGrade, @RequestParam(required = false) String courseId) throws GradeException {
        return gradeService.getStudentTotalMean(ltiSession, studentId, isCurrentGrade, courseId);
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

    @RequestMapping(value = "/sendMessageToUsers", method = RequestMethod.POST)
    public boolean sendMessageToUsers(@RequestBody String jsonData, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        if (securityService.isFaculty(lld.getRolesList())) {
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

            } catch (IOException ex) {
                log.warn("Conversation cannot be created");
                throw new GradeException();
            }

        } else {
            log.warn("User role is not valid");
            throw new GradeException();
        }
        return true;
    }
}

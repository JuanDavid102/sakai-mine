package edu.uc.ltigradebook.controller;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;
import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.exception.AccountException;
import edu.uc.ltigradebook.service.CourseService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.SecurityService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CourseRestController {

    @Autowired
    CourseService courseService;

    @Autowired
    EventTrackingService eventTrackingService;

    @Autowired
    SecurityService securityService;

    @RequestMapping(value = "/saveCourseConversionScale", method = RequestMethod.POST)
    public boolean saveCourseConversionScale(@RequestParam String newConversionScale, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession)  {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String userId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).put("newConversionScale", newConversionScale).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying to save a conversion scale, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, userId, StringUtils.EMPTY, eventDetails);
            return false;
        }

        log.info("Set conversion scale to {} for the course {}.", newConversionScale, courseId);
        CoursePreference coursePreferences = courseService.getCoursePreference(courseId);
        switch (newConversionScale) {
            case "FIFTY":
                coursePreferences.setConversionScale(ScaleConstants.FIFTY);
                break;
            case "SIXTY":
                coursePreferences.setConversionScale(ScaleConstants.SIXTY);
                break;
            case "SEVENTY":
                coursePreferences.setConversionScale(ScaleConstants.SEVENTY);
                break;
            case "EIGHTY":
                coursePreferences.setConversionScale(ScaleConstants.EIGHTY);
                break;
            case "NINETY":
                coursePreferences.setConversionScale(ScaleConstants.NINETY);
                break;
            default:
                log.error("Conversion scale not recognized {}.", newConversionScale);
                break;
        }

        courseService.saveCoursePreference(coursePreferences);

        // Post an event
        eventTrackingService.postEvent(EventConstants.INSTRUCTOR_SAVE_COURSE_SCALE, userId, courseId, eventDetails);
        return true;
    }

}

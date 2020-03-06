package edu.uc.ltigradebook.controller;

import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.entity.Event;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.SecurityService;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
public class EventController {

    @Autowired
    private EventTrackingService eventTrackingService;

    @Autowired
    private SecurityService securityService;

    @RequestMapping(value = "/getCourseGradeEvents", method = {RequestMethod.POST, RequestMethod.GET})
    public List<Event> getCourseGradeEvents(@ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws GradeException {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying get course grade events, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, canvasUserId, courseId, eventDetails);
            return null;
        }

        List<String> eventTypes = new ArrayList<>();
        eventTypes.add(EventConstants.INSTRUCTOR_POST_GRADE);
        eventTypes.add(EventConstants.INSTRUCTOR_DELETE_GRADE);
        eventTypes.add(EventConstants.IMPORT_POST_GRADE);
        eventTypes.add(EventConstants.IMPORT_DELETE_GRADE);
        return eventTrackingService.getAllEventsByEventCourseAndEventTypes(courseId, eventTypes);
    }
}

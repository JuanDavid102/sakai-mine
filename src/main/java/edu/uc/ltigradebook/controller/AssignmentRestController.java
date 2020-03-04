package edu.uc.ltigradebook.controller;

import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;
import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.service.AssignmentService;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.SecurityService;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AssignmentRestController {

    @Autowired
    AssignmentService assignmentService;

    @Autowired
    EventTrackingService eventTrackingService;

    @Autowired
    SecurityService securityService;

    @RequestMapping(value = "/saveAssignmentConversionScale", method = RequestMethod.POST)
    public boolean saveAssignmentConversionScale(@RequestParam long assignmentId, @RequestParam String newConversionScale, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession)  {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String userId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).put("assignmentId", assignmentId).put("newConversionScale", newConversionScale).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying to save a conversion scale, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, userId, courseId, eventDetails);
            return false;
        }

        AssignmentPreference assignmentPreference;
        Optional<AssignmentPreference> optionalAssignmentPref = assignmentService.getAssignmentPreference(Long.toString(assignmentId));
        if (optionalAssignmentPref.isPresent()) {
            assignmentPreference = optionalAssignmentPref.get();
        } else {
            assignmentPreference = new AssignmentPreference();
            assignmentPreference.setAssignmentId(assignmentId);
        }
        switch (newConversionScale) {
            case "":
                assignmentPreference.setConversionScale("");
                break;
            case ScaleConstants.FIFTY:
                assignmentPreference.setConversionScale(ScaleConstants.FIFTY);
                break;
            case ScaleConstants.SIXTY:
                assignmentPreference.setConversionScale(ScaleConstants.SIXTY);
                break;
            case ScaleConstants.SEVENTY:
                assignmentPreference.setConversionScale(ScaleConstants.SEVENTY);
                break;
            case ScaleConstants.EIGHTY:
                assignmentPreference.setConversionScale(ScaleConstants.EIGHTY);
                break;
            case ScaleConstants.NINETY:
                assignmentPreference.setConversionScale(ScaleConstants.NINETY);
                break;
            default:
                log.error("Conversion scale not recognized {}.", newConversionScale);
                break;
        }

        assignmentService.saveAssignmentPreference(assignmentPreference);

        // Post an event
        eventTrackingService.postEvent(EventConstants.INSTRUCTOR_SAVE_ASSIGNMENT_SCALE, userId, courseId, eventDetails);
        return true;
    }

    @RequestMapping(value = "/saveAssignmentMuted", method = RequestMethod.POST)
    public boolean saveAssignmentMuted(@RequestParam long assignmentId, @RequestParam boolean muted, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession)  {
        String courseId = ltiSession.getCanvasCourseId();
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String userId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String eventDetails = new JSONObject().put("courseId", courseId).put("assignmentId", assignmentId).put("muted", muted).toString();

        if (!securityService.isFaculty(lld.getRolesList())) {
            log.error("Security error when trying to mute an assignment, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, userId, courseId, eventDetails);
            return false;
        }

        AssignmentPreference assignmentPreference;
        Optional<AssignmentPreference> optionalAssignmentPref = assignmentService.getAssignmentPreference(Long.toString(assignmentId));
        if (optionalAssignmentPref.isPresent()) {
            assignmentPreference = optionalAssignmentPref.get();
        } else {
            assignmentPreference = new AssignmentPreference();
            assignmentPreference.setAssignmentId(assignmentId);
        }
        assignmentPreference.setMuted(muted);

        // Post an event
        eventTrackingService.postEvent(EventConstants.INSTRUCTOR_SAVE_ASSIGNMENT_MUTE, userId, courseId, eventDetails);
        assignmentService.saveAssignmentPreference(assignmentPreference);
        return true;
    }

}

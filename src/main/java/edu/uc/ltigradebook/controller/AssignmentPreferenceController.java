package edu.uc.ltigradebook.controller;

import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.service.AssignmentService;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AssignmentPreferenceController {

    @Autowired
    AssignmentService assignmentService;

    @RequestMapping(value = "/saveAssignmentConversionScale", method = RequestMethod.POST)
    public boolean saveAssignmentConversionScale(@RequestParam long assignmentId, @RequestParam String newConversionScale, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession)  {
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
        return true;
    }

    @RequestMapping(value = "/saveAssignmentMuted", method = RequestMethod.POST)
    public boolean saveAssignmentConversionScale(@RequestParam long assignmentId, @RequestParam boolean muted, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession)  {
        AssignmentPreference assignmentPreference;
        Optional<AssignmentPreference> optionalAssignmentPref = assignmentService.getAssignmentPreference(Long.toString(assignmentId));
        if (optionalAssignmentPref.isPresent()) {
            assignmentPreference = optionalAssignmentPref.get();
        } else {
            assignmentPreference = new AssignmentPreference();
            assignmentPreference.setAssignmentId(assignmentId);
        }
        assignmentPreference.setMuted(muted);;
        assignmentService.saveAssignmentPreference(assignmentPreference);
        return true;
    }

}

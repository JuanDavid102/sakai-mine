package edu.uc.ltigradebook.controller;

import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;

import edu.uc.ltigradebook.constants.ScaleConstant;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.CoursePreference;
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
            case ScaleConstant.FIFTY:
                assignmentPreference.setConversionScale(ScaleConstant.FIFTY);
                break;
            case ScaleConstant.SIXTY:
                assignmentPreference.setConversionScale(ScaleConstant.SIXTY);
                break;
            case ScaleConstant.SEVENTY:
                assignmentPreference.setConversionScale(ScaleConstant.SEVENTY);
                break;
            case ScaleConstant.EIGHTY:
                assignmentPreference.setConversionScale(ScaleConstant.EIGHTY);
                break;
            case ScaleConstant.NINETY:
                assignmentPreference.setConversionScale(ScaleConstant.NINETY);
                break;
            default:
                log.error("Conversion scale not recognized {}.", newConversionScale);
                break;
        }
        assignmentService.saveAssignmentPreference(assignmentPreference);
        return true;
    }

}

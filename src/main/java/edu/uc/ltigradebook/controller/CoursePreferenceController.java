package edu.uc.ltigradebook.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;
import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.service.CourseService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class CoursePreferenceController {

    @Autowired
    CourseService courseService;

    @RequestMapping(value = "/saveCourseConversionScale", method = RequestMethod.POST)
    public boolean saveCourseConversionScale(@RequestParam String newConversionScale, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession)  {
        String courseId = ltiSession.getCanvasCourseId();
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
        return true;
    }

}

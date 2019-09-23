package edu.uc.ltigradebook.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.uc.ltigradebook.constants.ScaleConstant;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.repository.CourseRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    public long getCourseCount() {
        log.debug("Getting the course count from the table.");
        return courseRepository.getCourseCount();
    }
    
    public CoursePreference getCoursePreference(String courseId) {
        log.debug("Getting course preferences by courseId {}.", courseId);
        Optional<CoursePreference> courseOptional = courseRepository.findById(Long.valueOf(courseId));
        if(!courseOptional.isPresent()) {
            log.debug("The course preference {} doesn't exist, creating it by default.", courseId);
            CoursePreference coursePreference = new CoursePreference();
            coursePreference.setCourseId(Long.valueOf(courseId));
            coursePreference.setConversionScale(ScaleConstant.DEFAULT);
            courseRepository.save(coursePreference);
            return coursePreference;
        }

        return courseOptional.get();
    }
    
    public void saveCoursePreference(CoursePreference coursePreference) {
        log.debug("Saving course preferences {}.", coursePreference);
        courseRepository.save(coursePreference);
    }
}

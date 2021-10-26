package edu.uc.ltigradebook.service;

import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.repository.CourseRepository;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    public long getCourseCount() {
        log.debug("Getting the course count from the table.");
        return courseRepository.getCourseCount();
    }

    // Returns all the present preferences in the table, this basically returns all the courses where the LTI is being used.
    public List<CoursePreference> getAllCoursePreferences() {
        return StreamSupport.stream(courseRepository.findAll().spliterator(), false).collect(Collectors.toList());
    }

    // Gets the course preferences or creates a new one if doesnt exist preferences.
    public CoursePreference getCoursePreference(String courseId) {
        log.debug("Getting course preferences by courseId {}.", courseId);
        Optional<CoursePreference> courseOptional = courseRepository.findById(Long.valueOf(courseId));
        if(!courseOptional.isPresent()) {
            log.debug("The course preference {} doesn't exist, creating it by default.", courseId);
            CoursePreference coursePreference = new CoursePreference();
            coursePreference.setCourseId(Long.valueOf(courseId));
            coursePreference.setConversionScale(ScaleConstants.DEFAULT);
            courseRepository.save(coursePreference);
            return coursePreference;
        }

        return courseOptional.get();
    }

    // Updates the course preference name, this is super useful for performance reasons so we don't need to ask Canvas again.
    public void updateCourseName(CoursePreference coursePreference, String courseName) {
        log.debug("Updating course name for course {}", courseName);
        if (coursePreference != null && StringUtils.isNotBlank(courseName)) {
            coursePreference.setCourseName(courseName);
            courseRepository.save(coursePreference);
        }
    }

    public void saveCoursePreference(CoursePreference coursePreference) {
        log.debug("Saving course preferences {}.", coursePreference);
        courseRepository.save(coursePreference);
    }
}

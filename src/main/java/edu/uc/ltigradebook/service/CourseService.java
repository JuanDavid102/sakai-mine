package edu.uc.ltigradebook.service;

import edu.ksu.canvas.model.Course;

import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.repository.CourseRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    public long getCourseCount() {
        log.debug("Getting the course count from the table.");
        return courseRepository.getCourseCount();
    }

    public List<Course> getAllCourses() throws IOException {
        List<Course> courses = new ArrayList<>();
        Iterable<CoursePreference> coursePreferences = (List<CoursePreference>) courseRepository.findAll();
        for (CoursePreference coursePreference : coursePreferences) {
            Optional<Course> optCourse = canvasService.getSingleCourse(Long.toString(coursePreference.getCourseId()));
            if (optCourse.isPresent()) {
                courses.add(optCourse.get());
            }
        }
        return courses;
    }

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
    
    public void saveCoursePreference(CoursePreference coursePreference) {
        log.debug("Saving course preferences {}.", coursePreference);
        courseRepository.save(coursePreference);
    }
}

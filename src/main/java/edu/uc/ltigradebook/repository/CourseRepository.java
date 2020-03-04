package edu.uc.ltigradebook.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import edu.uc.ltigradebook.entity.CoursePreference;

@Repository
public interface CourseRepository extends CrudRepository<CoursePreference, Long> {
    @Query(value = "select count(distinct(course_id)) from lti_gb_course_prefs", nativeQuery=true)
    Long getCourseCount();
}

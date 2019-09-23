package edu.uc.ltigradebook.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import edu.uc.ltigradebook.entity.CoursePreference;

@Repository
public interface CourseRepository extends CrudRepository<CoursePreference, Long> {

    @Query(value = "select count(distinct(event_course)) from gradebook_events where event_course is not null and event_course <> ''", nativeQuery=true)
    Long getCourseCount();

}
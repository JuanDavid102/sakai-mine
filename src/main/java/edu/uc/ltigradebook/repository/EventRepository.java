package edu.uc.ltigradebook.repository;

import edu.uc.ltigradebook.entity.Event;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends CrudRepository<Event, Long> {
    public List<Event> findAllByEventCourseAndEventTypeIn(String eventCourse, List<String> eventTypes);
}

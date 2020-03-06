package edu.uc.ltigradebook.service;

import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import edu.uc.ltigradebook.constants.CacheConstants;
import edu.uc.ltigradebook.entity.Event;
import edu.uc.ltigradebook.repository.EventRepository;

@Service
@Slf4j
public class EventTrackingService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Cacheable(CacheConstants.EVENTS)
    public Iterable<Event> getAllEvents() {
        log.debug("Getting all the events from the table.");
        return eventRepository.findAll();
    }

    public List<Event> getAllEventsByEventCourseAndEventTypes(String eventCourse, List<String> eventTypes) {
        log.debug("Getting all the events from course {} and event types {}.", eventCourse, eventTypes);
        List<Event> events = eventRepository.findAllByEventCourseAndEventTypeIn(eventCourse, eventTypes);
        try {
            List<Assignment> assignmentList = canvasService.listCourseAssignments(eventCourse);
            List<User> users = new ArrayList(canvasService.getUsersInCourse(eventCourse));
            users.addAll(canvasService.getTeachersInCourse(eventCourse));
            for (Event event : events) {
                JSONObject jsonEvent = new JSONObject(event.getEventDetails());
                Optional<Assignment> assignment = assignmentList.stream().filter(asn -> jsonEvent.getString("assignmentId").equals(Integer.toString(asn.getId()))).findFirst();
                Optional<User> student = users.stream().filter(usr -> jsonEvent.getString("userId").equals(Integer.toString(usr.getId()))).findFirst();
                Optional<User> teacher = users.stream().filter(usr -> event.getEventUser().equals(Integer.toString(usr.getId()))).findFirst();
                if (assignment.isPresent()) {
                    jsonEvent.put("assignmentName", assignment.get().getName());
                }
                if (student.isPresent()) {
                    jsonEvent.put("studentName", student.get().getName());
                }
                if (teacher.isPresent()) {
                    jsonEvent.put("teacherName", teacher.get().getName());
                }
                event.setEventDetails(jsonEvent.toString());
            }

        } catch (Exception ex) {
            log.error("Cannot get events for course {}", eventCourse);
        }
        return events;
    }

    @Cacheable(CacheConstants.SINGLE_EVENT)
    public Optional<Event> getEventById(long eventId) {
        log.debug("Getting an event from the table by id {}.", eventId);
        return eventRepository.findById(eventId);
    }

    public void postEvent(String eventType, String eventUser, String eventCourse, String eventDetails) {
        log.debug("Event {} posted for the user {} with details {}.", eventType, eventUser, eventDetails);
        Event event = new Event();
        event.setEventType(eventType);
        event.setEventUser(eventUser);
        event.setEventCourse(eventCourse);
        event.setEventDetails(eventDetails);
        event.setEventDate(Instant.now());
        eventRepository.save(event);
    }

    public void postEvent(String eventType, String eventUser) {
        this.postEvent(eventType, eventUser, StringUtils.EMPTY, StringUtils.EMPTY);
    }

    public void postEvent(String eventType, String eventUser, String eventCourse) {
        this.postEvent(eventType, eventUser, eventCourse, StringUtils.EMPTY);
    }

    public long getEventCount() {
        log.debug("Getting the event count from the table.");
        return eventRepository.count();
    }

}

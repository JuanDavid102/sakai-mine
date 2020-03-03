package edu.uc.ltigradebook.service;

import java.time.Instant;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import edu.uc.ltigradebook.constants.CacheConstants;
import edu.uc.ltigradebook.entity.Event;
import edu.uc.ltigradebook.repository.EventRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EventTrackingService {

    @Autowired
    private EventRepository eventRepository;

    @Cacheable(CacheConstants.EVENTS)
    public Iterable<Event> getAllEvents() {
        log.debug("Getting all the events from the table.");
        return eventRepository.findAll();
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

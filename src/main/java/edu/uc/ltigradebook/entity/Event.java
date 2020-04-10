package edu.uc.ltigradebook.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lti_gb_events")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="event_generator")
    @SequenceGenerator(name="event_generator", sequenceName="lti_gb_event_seq", allocationSize=1)
    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_date")
    private Instant eventDate;

    @Column(name = "event_user")
    private String eventUser;

    @Column(name = "event_course")
    private String eventCourse;

    @Column(name = "event_details", length=1000)
    private String eventDetails;

}

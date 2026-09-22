package com.jarvis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Calendar Event entity for JARVIS schedule manager.
 * Stores meetings, appointments, deadlines, and routines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "calendar_events")
public class CalendarEvent {

    @Id
    private String id;

    @Indexed
    @Builder.Default
    private String userId = "default";

    @Indexed
    private String title;

    private String description;
    private String location;
    private String eventTime;
    private String googleCalendarUrl;

    @Builder.Default
    private Instant createdAt = Instant.now();
}

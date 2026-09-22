package com.jarvis.repository;

import com.jarvis.model.CalendarEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {
    List<CalendarEvent> findByUserIdOrderByCreatedAtDesc(String userId);
    List<CalendarEvent> findByUserIdAndTitleContainingIgnoreCase(String userId, String query);
}

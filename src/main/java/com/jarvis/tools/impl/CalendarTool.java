package com.jarvis.tools.impl;

import com.jarvis.model.CalendarEvent;
import com.jarvis.repository.CalendarEventRepository;
import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calendar & Daily Schedule Management Tool.
 * Creates Google Calendar events with pre-filled details and maintains a persistent agenda in MongoDB.
 */
@Component
public class CalendarTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(CalendarTool.class);

    @Autowired
    private CalendarEventRepository calendarRepo;

    @Override
    public String getName() {
        return "calendar_tool";
    }

    @Override
    public String getDescription() {
        return "Manage your calendar and daily schedule: create Google Calendar events (action='create_event'), view your agenda (action='list_agenda'), or clear events (action='delete_event').";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("create_event", "list_agenda", "delete_event"),
                                "description", "Action to perform: 'create_event', 'list_agenda', or 'delete_event'"
                        ),
                        "title", Map.of(
                                "type", "string",
                                "description", "Event title or meeting subject (e.g. 'Meeting with client', 'Design sprint', 'Doctor appointment')"
                        ),
                        "time", Map.of(
                                "type", "string",
                                "description", "Date and time of the event (e.g. 'tomorrow at 3 PM', '2026-09-23 15:00', 'Friday 10:00 AM')"
                        ),
                        "duration_minutes", Map.of(
                                "type", "integer",
                                "description", "Duration of the event in minutes (default 60)"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "Optional notes or details for the calendar event"
                        ),
                        "location", Map.of(
                                "type", "string",
                                "description", "Optional meeting link or physical location"
                        )
                ),
                "required", List.of("action")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String action = (String) params.get("action");
        if (action == null || action.isBlank()) {
            return ToolResult.failure("Action is required for calendar_tool.");
        }

        action = action.toLowerCase(Locale.ROOT).trim();

        try {
            switch (action) {
                case "create_event" -> {
                    String title = (String) params.get("title");
                    if (title == null || title.isBlank()) {
                        title = "New Appointment";
                    }

                    String timeStr = (String) params.get("time");
                    if (timeStr == null || timeStr.isBlank()) {
                        timeStr = "Today";
                    }

                    String desc = (String) params.getOrDefault("description", "Scheduled via JARVIS Personal Assistant");
                    String loc = (String) params.getOrDefault("location", "Online");
                    Number durNum = (Number) params.getOrDefault("duration_minutes", 60);
                    int durationMins = durNum.intValue();

                    // Calculate date range for Google Calendar template
                    LocalDateTime start = LocalDateTime.now().plusHours(1);
                    LocalDateTime end = start.plusMinutes(durationMins);
                    DateTimeFormatter gCalFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
                    String startUtc = start.atOffset(ZoneOffset.UTC).format(gCalFmt);
                    String endUtc = end.atOffset(ZoneOffset.UTC).format(gCalFmt);

                    String gCalUrl = "https://calendar.google.com/calendar/render?action=TEMPLATE"
                            + "&text=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                            + "&dates=" + startUtc + "/" + endUtc
                            + "&details=" + URLEncoder.encode(desc, StandardCharsets.UTF_8)
                            + "&location=" + URLEncoder.encode(loc, StandardCharsets.UTF_8);

                    // Save local MongoDB record
                    CalendarEvent event = CalendarEvent.builder()
                            .userId("default")
                            .title(title)
                            .description(desc)
                            .location(loc)
                            .eventTime(timeStr)
                            .googleCalendarUrl(gCalUrl)
                            .build();
                    calendarRepo.save(event);

                    // Open Google Calendar in Chrome
                    String command = "cmd /c start chrome \"" + gCalUrl + "\"";
                    Runtime.getRuntime().exec(command);
                    log.info("[CalendarTool] Launched Google Calendar event: '{}'", title);

                    String summary = "Scheduled '" + title + "' (" + timeStr + ") on your calendar, sir. Google Calendar is open and ready to save.";
                    return ToolResult.success(summary, Map.of("title", title, "time", timeStr, "url", gCalUrl), null);
                }

                case "list_agenda" -> {
                    List<CalendarEvent> events = calendarRepo.findByUserIdOrderByCreatedAtDesc("default");
                    if (events.isEmpty()) {
                        return ToolResult.success("Your schedule is currently completely clear, sir. No upcoming meetings or events recorded.", Map.of("count", 0), null);
                    }

                    StringBuilder sb = new StringBuilder("Here is your current recorded agenda:\n");
                    int i = 1;
                    for (CalendarEvent e : events) {
                        sb.append(String.format(Locale.ROOT, "%d. %s — Time: %s%s\n",
                                i++, e.getTitle(), e.getEventTime(),
                                (e.getLocation() != null && !e.getLocation().isBlank()) ? " (" + e.getLocation() + ")" : ""));
                        if (i > 6) break;
                    }
                    return ToolResult.success(sb.toString().trim(), Map.of("events", events), null);
                }

                case "delete_event" -> {
                    String title = (String) params.get("title");
                    if (title != null && !title.isBlank()) {
                        List<CalendarEvent> matches = calendarRepo.findByUserIdAndTitleContainingIgnoreCase("default", title);
                        if (!matches.isEmpty()) {
                            calendarRepo.deleteAll(matches);
                            return ToolResult.success("Removed '" + title + "' from your agenda, sir.", Map.of("deleted", matches.size()), null);
                        }
                    }
                    return ToolResult.failure("No matching event found to delete.");
                }

                default -> {
                    return ToolResult.failure("Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            log.error("[CalendarTool] Calendar execution error: {}", e.getMessage(), e);
            return ToolResult.failure("Calendar tool error: " + e.getMessage());
        }
    }

    @Override
    public boolean isEffectful() {
        return true;
    }
}

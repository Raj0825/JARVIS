package com.jarvis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tool_calls")
public class ToolCallRecord {

    public enum Status {
        ALLOWED, DENIED, ERROR
    }

    @Id
    private String id;

    @Indexed
    private String conversationId;

    private String messageId;

    private String toolName;

    /** JSON-serialised parameters passed to the tool */
    private String paramsJson;

    /** JSON-serialised result returned by the tool */
    private String resultJson;

    private Status status;

    /** Human-readable reason if status == DENIED */
    private String denyReason;

    @CreatedDate
    private Instant createdAt;

    private long executionMs;
}

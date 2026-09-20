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
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {

    public enum Role {
        USER, ASSISTANT, TOOL, SYSTEM
    }

    @Id
    private String id;

    @Indexed
    private String conversationId;

    private Role role;

    private String content;

    /** Tool name — populated when role == TOOL */
    private String toolName;

    /** Tool call ID — links assistant's tool call to tool response */
    private String toolCallId;

    /** Tool calls the LLM requested (when role == ASSISTANT) */
    @Builder.Default
    private List<ToolCallRequest> toolCallRequests = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    /** Holds a single tool call request from the LLM */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRequest {
        private String id;
        private String name;
        private String argumentsJson;
    }
}

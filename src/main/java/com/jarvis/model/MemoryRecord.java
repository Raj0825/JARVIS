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
 * Persistent memory entity for JARVIS memory vault.
 * Stores facts, credentials, reminders, links, or notes dictated by the user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "memories")
public class MemoryRecord {

    @Id
    private String id;

    @Indexed
    @Builder.Default
    private String userId = "default";

    @Indexed
    private String topic;

    private String content;

    @Builder.Default
    private String category = "general"; // general | credential | reminder | link | note

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}

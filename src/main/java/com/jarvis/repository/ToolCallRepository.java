package com.jarvis.repository;

import com.jarvis.model.ToolCallRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ToolCallRepository extends MongoRepository<ToolCallRecord, String> {
    List<ToolCallRecord> findByConversationIdOrderByCreatedAtDesc(String conversationId);
    List<ToolCallRecord> findTop50ByOrderByCreatedAtDesc();
}

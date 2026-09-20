package com.jarvis.repository;

import com.jarvis.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    List<Conversation> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Conversation> findByUserIdAndActiveOrderByCreatedAtDesc(String userId, boolean active);
}

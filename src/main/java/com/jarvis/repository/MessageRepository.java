package com.jarvis.repository;

import com.jarvis.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    List<Message> findTop30ByConversationIdOrderByCreatedAtDesc(String conversationId);
    void deleteByConversationId(String conversationId);
}

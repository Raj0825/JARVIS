package com.jarvis.repository;

import com.jarvis.model.MemoryRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryRepository extends MongoRepository<MemoryRecord, String> {
    List<MemoryRecord> findByUserIdOrderByCreatedAtDesc(String userId);
    List<MemoryRecord> findByUserIdAndTopicContainingIgnoreCase(String userId, String topic);
    List<MemoryRecord> findByUserIdAndContentContainingIgnoreCase(String userId, String query);
}

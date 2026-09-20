package com.jarvis.repository;

import com.jarvis.model.JarvisSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SettingsRepository extends MongoRepository<JarvisSettings, String> {
    Optional<JarvisSettings> findByUserId(String userId);
}

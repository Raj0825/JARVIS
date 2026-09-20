package com.jarvis.controller;

import com.jarvis.model.Conversation;
import com.jarvis.model.Message;
import com.jarvis.repository.ConversationRepository;
import com.jarvis.repository.MessageRepository;
import com.jarvis.service.ChatOrchestratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired private ConversationRepository conversationRepo;
    @Autowired private MessageRepository messageRepo;
    @Autowired private ChatOrchestratorService orchestrator;

    @GetMapping
    public ResponseEntity<List<Conversation>> listConversations() {
        return ResponseEntity.ok(conversationRepo.findByUserIdOrderByCreatedAtDesc("default"));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createConversation(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "New Conversation");
        String id = orchestrator.createConversation(title);
        return ResponseEntity.ok(Map.of("conversationId", id, "title", title));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Message>> getMessages(@PathVariable String id) {
        return ResponseEntity.ok(messageRepo.findByConversationIdOrderByCreatedAtAsc(id));
    }
}

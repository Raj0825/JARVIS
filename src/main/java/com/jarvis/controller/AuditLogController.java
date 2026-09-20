package com.jarvis.controller;

import com.jarvis.model.ToolCallRecord;
import com.jarvis.repository.ToolCallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    @Autowired
    private ToolCallRepository toolCallRepository;

    @GetMapping
    public ResponseEntity<List<ToolCallRecord>> getRecentAuditLog() {
        return ResponseEntity.ok(toolCallRepository.findTop50ByOrderByCreatedAtDesc());
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<ToolCallRecord>> getAuditLogForConversation(@PathVariable String conversationId) {
        return ResponseEntity.ok(toolCallRepository.findByConversationIdOrderByCreatedAtDesc(conversationId));
    }
}

package com.jarvis.tools;

import com.jarvis.model.ToolCallRecord;
import com.jarvis.repository.ToolCallRepository;
import com.jarvis.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Permission gate and audit logger for all tool executions.
 * - Read-only tools (isEffectful=false) are always allowed.
 * - Effectful tools require allowWrites=true in settings.
 * - Every tool call attempt is logged to MongoDB.
 */
@Service
public class PermissionGateService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGateService.class);

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private ToolCallRepository toolCallRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Checks permission, executes the tool, and logs the result.
     *
     * @param tool           The tool to execute
     * @param params         Parsed parameters
     * @param conversationId Conversation context for audit record
     * @param messageId      Parent message ID for audit linkage
     * @return ToolResult (may be a denial result)
     */
    public ToolResult checkAndExecute(JarvisTool tool, Map<String, Object> params, String conversationId, String messageId) {
        boolean allowWrites = settingsService.getSettings("default").isAllowWrites();
        ToolCallRecord.Status status;
        ToolResult result;
        long start = System.currentTimeMillis();

        try {
            if (tool.isEffectful() && !allowWrites) {
                log.warn("[PermissionGate] Blocked effectful tool '{}' (allowWrites=false)", tool.getName());
                status = ToolCallRecord.Status.DENIED;
                result = ToolResult.failure("Tool '" + tool.getName() + "' is an effectful action. Enable 'Allow Write Actions' in Settings to permit it.");
            } else {
                log.info("[PermissionGate] Executing tool '{}' with params: {}", tool.getName(), params);
                result = tool.execute(params);
                status = result.isSuccess() ? ToolCallRecord.Status.ALLOWED : ToolCallRecord.Status.ERROR;
            }
        } catch (Exception e) {
            log.error("[PermissionGate] Tool '{}' threw exception: {}", tool.getName(), e.getMessage(), e);
            status = ToolCallRecord.Status.ERROR;
            result = ToolResult.failure("Execution error: " + e.getMessage());
        }

        long executionMs = System.currentTimeMillis() - start;

        // Persist audit record
        try {
            ToolCallRecord record = ToolCallRecord.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .toolName(tool.getName())
                    .paramsJson(mapper.writeValueAsString(params))
                    .resultJson(mapper.writeValueAsString(result.getSummary()))
                    .status(status)
                    .denyReason(status == ToolCallRecord.Status.DENIED ? result.getError() : null)
                    .executionMs(executionMs)
                    .build();
            toolCallRepository.save(record);
        } catch (Exception e) {
            log.warn("[PermissionGate] Failed to persist audit record: {}", e.getMessage());
        }

        log.info("[PermissionGate] Tool '{}' completed in {}ms, status={}", tool.getName(), executionMs, status);
        return result;
    }
}

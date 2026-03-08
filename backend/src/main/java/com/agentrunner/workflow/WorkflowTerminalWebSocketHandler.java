package com.agentrunner.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class WorkflowTerminalWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_RUN_ID = "runId";

    private final WorkflowRunService workflowRunService;
    private final ObjectMapper objectMapper;

    public WorkflowTerminalWebSocketHandler(
            WorkflowRunService workflowRunService,
            ObjectMapper objectMapper
    ) {
        this.workflowRunService = workflowRunService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String runId = extractRunId(session.getUri());
        if (runId == null || runId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        session.getAttributes().put(ATTR_RUN_ID, runId);
        try {
            workflowRunService.registerTerminalSession(runId, session);
        } catch (IllegalArgumentException e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason(e.getMessage()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String runId = (String) session.getAttributes().get(ATTR_RUN_ID);
        if (runId == null || runId.isBlank()) {
            runId = extractRunId(session.getUri());
        }

        if (runId == null || runId.isBlank()) {
            return;
        }

        Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
        Object typeValue = payload.get("type");
        if (!(typeValue instanceof String type) || !"stdin".equalsIgnoreCase(type)) {
            return;
        }

        String input = "";
        Object dataValue = payload.get("data");
        if (dataValue instanceof String s) {
            input = s;
        }

        boolean appendNewline = true;
        Object newlineValue = payload.get("appendNewline");
        if (newlineValue instanceof Boolean b) {
            appendNewline = b;
        }

        workflowRunService.sendTerminalInput(runId, input, appendNewline);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String runId = (String) session.getAttributes().get(ATTR_RUN_ID);
        if (runId != null && !runId.isBlank()) {
            workflowRunService.unregisterTerminalSession(runId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String runId = (String) session.getAttributes().get(ATTR_RUN_ID);
        if (runId != null && !runId.isBlank()) {
            workflowRunService.unregisterTerminalSession(runId, session);
        }

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private static String extractRunId(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }

        String path = uri.getPath();
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return null;
        }
        return URLDecoder.decode(path.substring(idx + 1), StandardCharsets.UTF_8);
    }
}

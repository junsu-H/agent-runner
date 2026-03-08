package com.agentrunner.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
class WorkflowEventBroadcaster {

    record TerminalWsEvent(
            String type, String runId, String status, String message,
            String currentStep, String currentCommand, String line, List<String> tail
    ) {
    }

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Set<WebSocketSession>> terminalSessions = new ConcurrentHashMap<>();

    WorkflowEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void registerSession(String runId, WebSocketSession session) {
        terminalSessions.computeIfAbsent(runId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    void unregisterSession(String runId, WebSocketSession session) {
        Set<WebSocketSession> sessions = terminalSessions.get(runId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            terminalSessions.remove(runId);
        }
    }

    void sendInitialSnapshot(WebSocketSession session, TerminalWsEvent event) {
        sendEvent(session, event);
    }

    void broadcastEvent(String runId, TerminalWsEvent event) {
        Set<WebSocketSession> sessions = terminalSessions.get(runId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        List<WebSocketSession> stale = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                stale.add(session);
                continue;
            }
            if (!sendEvent(session, event)) {
                stale.add(session);
            }
        }

        if (!stale.isEmpty()) {
            sessions.removeAll(stale);
            if (sessions.isEmpty()) {
                terminalSessions.remove(runId);
            }
        }
    }

    private boolean sendEvent(WebSocketSession session, TerminalWsEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            synchronized (session) {
                if (!session.isOpen()) {
                    return false;
                }
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

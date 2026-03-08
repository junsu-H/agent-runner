package com.agentrunner.config;

import com.agentrunner.workflow.WorkflowTerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WorkflowTerminalWebSocketConfig implements WebSocketConfigurer {

    private final WorkflowTerminalWebSocketHandler workflowTerminalWebSocketHandler;

    public WorkflowTerminalWebSocketConfig(WorkflowTerminalWebSocketHandler workflowTerminalWebSocketHandler) {
        this.workflowTerminalWebSocketHandler = workflowTerminalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(workflowTerminalWebSocketHandler, "/api/ws/workflows/runs/*")
                .setAllowedOriginPatterns("http://localhost:5173", "http://127.0.0.1:5173");
    }
}


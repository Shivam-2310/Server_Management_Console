package com.management.console.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.console.dto.DashboardDTO;
import com.management.console.dto.HealthCheckDTO;
import com.management.console.dto.MetricsDTO;
import com.management.console.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final DashboardService dashboardService;
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("WebSocket connection established: {}", session.getId());
        
        // Send initial dashboard data
        sendDashboardSummary(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("WebSocket connection closed: {} with status {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WebSocket message: {}", payload);
        
        try {
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            
            switch (wsMessage.getType()) {
                case "SUBSCRIBE_SERVICE" -> subscribeToService(session, wsMessage.getServiceId());
                case "UNSUBSCRIBE_SERVICE" -> unsubscribeFromService(session, wsMessage.getServiceId());
                case "GET_DASHBOARD" -> sendDashboardSummary(session);
                default -> log.warn("Unknown message type: {}", wsMessage.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process WebSocket message: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session.getId());
    }

    public void broadcastHealthUpdate(Long serviceId, HealthCheckDTO healthCheck) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("HEALTH_UPDATE");
        message.setServiceId(serviceId);
        message.setData(healthCheck);
        
        broadcastMessage(message);
    }

    public void broadcastMetricsUpdate(Long serviceId, MetricsDTO metrics) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("METRICS_UPDATE");
        message.setServiceId(serviceId);
        message.setData(metrics);
        
        broadcastMessage(message);
    }

    public void broadcastIncidentCreated(Object incident) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("INCIDENT_CREATED");
        message.setData(incident);
        
        broadcastMessage(message);
    }

    public void broadcastActionExecuted(Object action) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("ACTION_EXECUTED");
        message.setData(action);
        
        broadcastMessage(message);
    }

    public void broadcastDashboardSummary() {
        try {
            DashboardDTO dashboard = dashboardService.getDashboardSummary();
            
            WebSocketMessage message = new WebSocketMessage();
            message.setType("DASHBOARD_UPDATE");
            message.setData(dashboard);
            
            broadcastMessage(message);
        } catch (Exception e) {
            log.error("Failed to broadcast dashboard summary: {}", e.getMessage());
        }
    }

    private void sendDashboardSummary(WebSocketSession session) {
        try {
            DashboardDTO dashboard = dashboardService.getDashboardSummary();
            
            WebSocketMessage message = new WebSocketMessage();
            message.setType("DASHBOARD_UPDATE");
            message.setData(dashboard);
            
            sendMessage(session, message);
        } catch (Exception e) {
            log.error("Failed to send dashboard summary: {}", e.getMessage());
        }
    }

    private void subscribeToService(WebSocketSession session, Long serviceId) {
        // Store subscription in session attributes
        session.getAttributes().put("subscribedService", serviceId);
        log.debug("Session {} subscribed to service {}", session.getId(), serviceId);
    }

    private void unsubscribeFromService(WebSocketSession session, Long serviceId) {
        session.getAttributes().remove("subscribedService");
        log.debug("Session {} unsubscribed from service {}", session.getId(), serviceId);
    }

    private void broadcastMessage(WebSocketMessage message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket message: {}", e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(jsonMessage);
        
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    // Check if message is for a specific service and session is subscribed
                    Long subscribedService = (Long) session.getAttributes().get("subscribedService");
                    if (message.getServiceId() != null && subscribedService != null) {
                        if (!message.getServiceId().equals(subscribedService)) {
                            return; // Skip if not subscribed to this service
                        }
                    }
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.error("Failed to send WebSocket message to session {}: {}", 
                        session.getId(), e.getMessage());
            }
        });
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            log.error("Failed to send WebSocket message: {}", e.getMessage());
        }
    }

    public int getActiveConnectionCount() {
        return sessions.size();
    }

    // Inner class for WebSocket messages
    @lombok.Data
    public static class WebSocketMessage {
        private String type;
        private Long serviceId;
        private Object data;
    }
}


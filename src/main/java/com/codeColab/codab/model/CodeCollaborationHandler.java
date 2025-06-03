package com.codeColab.codab.model;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeCollaborationHandler extends TextWebSocketHandler {
    private static final Map<String, Map<WebSocketSession, String>> sessionMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = getSessionId(session);
        sessionMap.putIfAbsent(sessionId, new ConcurrentHashMap<>());
        sessionMap.get(sessionId).put(session, session.getId());

        broadcastUserList(sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = getSessionId(session);
        Map<String, Object> receivedMessage = objectMapper.readValue(message.getPayload(), Map.class);

        if ("CODE_CHANGE".equals(receivedMessage.get("type"))) {
            broadcastMessage(sessionId, message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = getSessionId(session);
        sessionMap.get(sessionId).remove(session);

        if (sessionMap.get(sessionId).isEmpty()) {
            sessionMap.remove(sessionId);
        } else {
            broadcastUserList(sessionId);
        }
    }

    private void broadcastMessage(String sessionId, String message) throws IOException {
        for (WebSocketSession s : sessionMap.get(sessionId).keySet()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(message));
            }
        }
    }

    private void broadcastUserList(String sessionId) throws IOException {
        List<String> users = sessionMap.get(sessionId).values().stream().collect(Collectors.toList());
        String userListMessage = objectMapper.writeValueAsString(Map.of("type", "USER_LIST", "data", users));

        for (WebSocketSession s : sessionMap.get(sessionId).keySet()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(userListMessage));
            }
        }
    }

    private String getSessionId(WebSocketSession session) {
        return session.getUri().getPath().split("/ws/code/")[1];
    }
}


package com.codeColab.codab.Controllers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SignalingHandler extends TextWebSocketHandler {
    private final Map<String, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserIdMap = new ConcurrentHashMap<>(); // Map to track session ID to user ID
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = extractRoomId(session);
        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        System.out.println("Session " + session.getId() + " joined room " + roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonNode jsonNode = objectMapper.readTree(payload);
        String roomId = "defaultVideoCallRoom"; // Default room ID

        // Extract message type and sender ID for logging
        String messageType = jsonNode.has("type") ? jsonNode.get("type").asText() : "unknown";
        String senderId = jsonNode.has("userId") ? jsonNode.get("userId").asText() : "unknown";
        String targetId = jsonNode.has("targetId") ? jsonNode.get("targetId").asText() : "none";

        // Store user ID mapping when a user joins
        if (messageType.equals("join") && !senderId.equals("unknown")) {
            sessionToUserIdMap.put(session.getId(), senderId);
            System.out.println("Mapped session " + session.getId() + " to user ID " + senderId);
        }

        // Prioritize roomId from payload if present and not empty
        if (jsonNode.has("roomId") && !jsonNode.get("roomId").asText().isEmpty()) {
            roomId = jsonNode.get("roomId").asText();
        } else {
            // Fallback to roomId from URI if not in payload
            String uriRoomId = extractRoomIdFromUri(session);
            if (uriRoomId != null && !uriRoomId.isEmpty()) {
                roomId = uriRoomId;
            }
        }

        System.out.println("Received message: Type=" + messageType + ", Sender=" + senderId + ", Target=" + targetId + ", Room=" + roomId);

        Map<String, WebSocketSession> currentRoomSessions = rooms.get(roomId);
        if (currentRoomSessions != null) {
            // Handle targeted messages (offer, answer, candidate)
            if (targetId != null && !targetId.equals("none")) {
                // Find the target session by user ID
                WebSocketSession targetSession = findSessionByUserId(currentRoomSessions, targetId);
                if (targetSession != null) {
                    synchronized (targetSession) {
                        if (targetSession.isOpen()) {
                            targetSession.sendMessage(message);
                            System.out.println("Sent " + messageType + " message from " + senderId + " to target " + targetId);
                        }
                    }
                } else {
                    System.out.println("Target user " + targetId + " not found in room " + roomId);
                }
            } else {
                // Broadcast messages (join, leave) to all except sender
                for (WebSocketSession s : currentRoomSessions.values()) {
                    if (!s.getId().equals(session.getId())) {
                        synchronized (s) {
                            if (s.isOpen()) {
                                s.sendMessage(message);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        String roomId = extractRoomId(session);
        Map<String, WebSocketSession> currentRoomSessions = rooms.get(roomId);
        if (currentRoomSessions != null) {
            // Get the user ID before removing the session
            String userId = sessionToUserIdMap.get(session.getId());

            // Remove the session from the room
            currentRoomSessions.remove(session.getId());
            System.out.println("Session " + session.getId() + " left room " + roomId);

            // Remove from the user ID mapping
            sessionToUserIdMap.remove(session.getId());

            // Notify others that this user has left
            if (userId != null) {
                TextMessage leaveMessage = new TextMessage(
                        objectMapper.writeValueAsString(Map.of(
                                "type", "leave",
                                "leavingUserId", userId,
                                "roomId", roomId
                        ))
                );

                for (WebSocketSession s : currentRoomSessions.values()) {
                    synchronized (s) {
                        if (s.isOpen()) {
                            s.sendMessage(leaveMessage);
                        }
                    }
                }
            }

            if (currentRoomSessions.isEmpty()) {
                rooms.remove(roomId);
                System.out.println("Room " + roomId + " is now empty and removed.");
            }
        }
    }

    private WebSocketSession findSessionByUserId(Map<String, WebSocketSession> sessions, String targetUserId) {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            String userId = sessionToUserIdMap.get(sessionId);
            if (targetUserId.equals(userId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extractRoomId(WebSocketSession session) {
        String path = session.getUri().getPath();
        UriTemplate template = new UriTemplate("/signal/{roomId}");
        Map<String, String> parameters = template.match(path);
        String roomId = parameters.get("roomId");
        return (roomId == null || roomId.isEmpty()) ? "defaultVideoCallRoom" : roomId; // Use default if not in URI
    }

    private String extractRoomIdFromUri(WebSocketSession session) {
        String path = session.getUri().getPath();
        UriTemplate template = new UriTemplate("/signal/{roomId}");
        Map<String, String> parameters = template.match(path);
        return parameters.get("roomId");
    }
}

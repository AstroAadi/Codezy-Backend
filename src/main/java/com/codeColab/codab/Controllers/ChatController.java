package com.codeColab.codab.Controllers;

import com.codeColab.codab.dto.ChatMessage;
import com.codeColab.codab.dto.CodeUpdate;
import com.codeColab.codab.dto.SystemMessage;
import com.codeColab.codab.entity.WsChatMessageType;
import com.codeColab.codab.service.CodeSharingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;


@CrossOrigin(origins = "https://codezy-e98c8.web.app")
@RestController
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private CodeSharingService codeSharingService;

    @MessageMapping("/chat/{sessionId}")
    @SendTo("/topic/chat/{sessionId}")
    public ChatMessage handleChat(@DestinationVariable String sessionId, ChatMessage message) {
        message.setTimestamp(new Date());
        return message;
    }

    @MessageMapping("/code/{sessionId}")
    @SendTo("/topic/code/{sessionId}")
    public CodeUpdate handleCodeUpdate(@DestinationVariable String sessionId, @Payload CodeUpdate update) {
        logger.info("Received code update for session: {} | filePath: {} | user: {}", sessionId, update.getFilePath(), update.getUsername());
        update.setSessionId(sessionId);
        update.setTimestamp(new Date());
        codeSharingService.saveCodeUpdate(update);
        logger.info("Saved code update for session: {} | filePath: {}", sessionId, update.getFilePath());
        return update;
    }

    // In CollaborationController.java or ChatController.java
@MessageMapping("/startCall/{sessionId}")
@SendTo("/topic/startCall/{sessionId}")
public void handleStartCall(@DestinationVariable String sessionId, Message message) {
    // Extract the message payload if needed
//    String payload = (String) message.getPayload();
//    logger.info("Broadcasting start call message for session: {}", sessionId);
//    // Logic to handle start call message
//    // Broadcast to all participants in the session
//    WebSocketSession[] sessions = new WebSocketSession[0];
//    for (WebSocketSession session : sessions) {
//        if (session.isOpen()) {
//            try {
//                session.sendMessage(new TextMessage(payload));
//            } catch (IOException e) {
//                logger.error("Error sending start call message to session: {}", session.getId(), e);
//            }
//        }
//    }
}
}


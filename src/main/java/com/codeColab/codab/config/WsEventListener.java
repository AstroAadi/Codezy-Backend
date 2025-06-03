package com.codeColab.codab.config;

import com.codeColab.codab.dto.ChatMessage;
import com.codeColab.codab.dto.ChatMessage;
import com.codeColab.codab.entity.WsChatMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WsEventListener {

    private final SimpMessageSendingOperations messageSendingOperations;

    @EventListener
    public void handleWsConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // Check if session attributes are available
        if (headerAccessor.getSessionAttributes() != null) {
            // Fetch the username if present
            String username = (String) headerAccessor.getSessionAttributes().get("username");
            System.out.println("User connected: " + username);
        } else {
            System.out.println("Session attributes are null.");
        }
    }

    @EventListener
    public void handleWsDisconnectListener(SessionDisconnectEvent event){

        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if(username != null){
            log.info("User Disconnected: {} ", username);

        }
    }
}
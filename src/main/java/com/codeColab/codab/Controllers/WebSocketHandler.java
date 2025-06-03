package com.codeColab.codab.Controllers;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class WebSocketHandler extends TextWebSocketHandler {
   private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
   private final ObjectMapper objectMapper = new ObjectMapper();

   @Override
   public void afterConnectionEstablished(WebSocketSession session) throws Exception {
       sessions.add(session);
       System.out.println("New connection: " + session.getId());
   }

   @Override
   public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
       System.out.println("Received: " + message.getPayload());

       for (WebSocketSession s : sessions) {
           if (s.isOpen()) {
               s.sendMessage(message);
           }
       }
   }

   @Override
   public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
       sessions.remove(session);
       System.out.println("Connection closed: " + session.getId());
   }
}

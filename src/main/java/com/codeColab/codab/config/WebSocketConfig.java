package com.codeColab.codab.config;

import com.codeColab.codab.Controllers.TerminalWebSocketHandler;
import com.codeColab.codab.service.CodeRunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private final CodeRunnerService codeRunnerService;

    @Autowired
    public WebSocketConfig(CodeRunnerService codeRunnerService) {
        this.codeRunnerService = codeRunnerService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:4200")
                .withSockJS();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Pass the codeRunnerService to the TerminalWebSocketHandler constructor
        registry.addHandler(new TerminalWebSocketHandler(codeRunnerService), "/terminal").setAllowedOrigins("*");
    }
}








package com.codeColab.codab.Controllers;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeColab.codab.service.CodeRunnerService;
import com.codeColab.codab.dto.CodeRequest;
import com.codeColab.codab.dto.CodeResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class TerminalWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Process process;
    private final CodeRunnerService codeRunnerService;
    private final ConcurrentHashMap<String, OutputStream> sessionInputs = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(CodeRunnerService codeRunnerService) {
        this.codeRunnerService = codeRunnerService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage("✅ Terminal WebSocket ready!\n"));
        } catch (Exception ignored) {}
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if (node.has("type") && node.get("type").asText().equals("input")) {
                String inputValue = node.get("value").asText();
                OutputStream stdin = sessionInputs.get(session.getId());
                if (stdin != null) {
                    stdin.write((inputValue + "\n").getBytes(StandardCharsets.UTF_8));
                    stdin.flush();
                }
                return;
            }
            String code = node.has("code") ? node.get("code").asText() : "";
            String fileName = node.has("fileName") ? node.get("fileName").asText() : "Main.txt";
            String language = getLanguageFromFileName(fileName);
            String input = node.has("input") ? node.get("input").asText() : "";

            session.sendMessage(new TextMessage("▶️ Running code in Docker...\n"));

            // Start the process and stream output
            Process process = codeRunnerService.startProcess(code, language, input, fileName);
            sessionInputs.put(session.getId(), process.getOutputStream());
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        session.sendMessage(new TextMessage(line + "\n"));
                    }
                    session.sendMessage(new TextMessage("\n[Process completed]\n"));
                } catch (Exception ignored) {}
            }).start();
        } catch (Exception e) {
            try {
                session.sendMessage(new TextMessage("❌ Error: " + e.getMessage() + "\n"));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionInputs.remove(session.getId());
    }

    private String getLanguageFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            switch (extension) {
                case "py": return "python";
                case "java": return "java";
                case "js": return "node";
                case "c": return "c";
                default: return "text";
            }
        }
        return "text";
    }
}

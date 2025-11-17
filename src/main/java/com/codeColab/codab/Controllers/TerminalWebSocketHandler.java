package com.codeColab.codab.Controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeColab.codab.service.CodeRunnerService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for code execution only.
 * Terminal functionality is handled by WebContainers in the frontend.
 */
public class TerminalWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CodeRunnerService codeRunnerService;
    private final ConcurrentHashMap<String, OutputStream> sessionInputs = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(CodeRunnerService codeRunnerService) {
        this.codeRunnerService = codeRunnerService;
        logger.info("✅ WebSocketHandler initialized (Code Execution Only)");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("🔌 WebSocket connection established - sessionId: {}", session.getId());

        try {
            session.sendMessage(new TextMessage("✅ WebSocket ready for code execution!\n"));
            logger.info("✅ Welcome message sent to session: {}", session.getId());
        } catch (Exception e) {
            logger.error("❌ Error sending welcome message: {}", e.getMessage());
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        logger.info("📨 WebSocket message received - sessionId: {}", sessionId);

        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            logger.info("📦 Parsed JSON - type: {}", node.has("type") ? node.get("type").asText() : "N/A");

            // Only handle code execution requests
            if (node.has("type") && "code-execution".equals(node.get("type").asText())) {
                logger.info("💻 Processing code execution request (Docker)");

                String code = node.has("code") ? node.get("code").asText() : "";
                String fileName = node.has("fileName") ? node.get("fileName").asText() : "Main.txt";
                String language = getLanguageFromFileName(fileName);
                String input = node.has("input") ? node.get("input").asText() : "";

                logger.info("📄 fileName: {}, language: {}, hasInput: {}", fileName, language, !input.isEmpty());

                session.sendMessage(new TextMessage("▶️ Running code in Docker...\r\n"));

                Process process = codeRunnerService.startProcess(code, language, input, fileName);

                if (process != null && process.getOutputStream() != null) {
                    sessionInputs.put(session.getId(), process.getOutputStream());
                    logger.info("✅ Process started and output stream stored");
                } else {
                    logger.error("❌ Failed to start process");
                    session.sendMessage(new TextMessage("❌ Failed to start code execution\r\n"));
                    return;
                }

                // Stream process output
                new Thread(() -> {
                    logger.info("🔄 Starting output stream for code execution");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        int lineCount = 0;
                        while ((line = reader.readLine()) != null) {
                            lineCount++;
                            logger.debug("📤 CODE OUTPUT Line {}: {}", lineCount, line);
                            session.sendMessage(new TextMessage(line + "\r\n"));
                        }
                        session.sendMessage(new TextMessage("\r\n[Process completed]\r\n"));
                        logger.info("🏁 Code execution completed (total lines: {})", lineCount);
                    } catch (Exception e) {
                        logger.error("❌ Error streaming process output: {}", e.getMessage(), e);
                    }
                }, "code-exec-" + sessionId).start();
            } else {
                logger.warn("⚠️ Unsupported message type or missing type field");
                session.sendMessage(new TextMessage("⚠️ Unsupported request type\r\n"));
            }

        } catch (Exception e) {
            logger.error("❌ Error in handleTextMessage: {}", e.getMessage(), e);
            try {
                session.sendMessage(new TextMessage("❌ Error: " + e.getMessage() + "\r\n"));
            } catch (Exception ignored) {
                logger.error("❌ Failed to send error message to client");
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        logger.info("🔌 WebSocket connection closed - sessionId: {}, status: {}", sessionId, status);

        sessionInputs.remove(sessionId);
        logger.info("🧹 Cleaned up session: {}", sessionId);
    }

    private String getLanguageFromFileName(String fileName) {
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".js")) return "javascript";
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".c")) return "c";
        if (fileName.endsWith(".cpp")) return "cpp";
        return "text";
    }
}
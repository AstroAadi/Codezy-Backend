package com.codeColab.codab.Controllers;

import com.codeColab.codab.service.CodeRunnerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * STOMP controller that accepts messages from frontend at /app/terminal/{sessionId}
 * and publishes output to /topic/terminal/{sessionId}.
 */
@Controller
public class TerminalStompController {
    private static final Logger logger = LoggerFactory.getLogger(TerminalStompController.class);

    private final CodeRunnerService codeRunnerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TerminalStompController(CodeRunnerService codeRunnerService, SimpMessagingTemplate messagingTemplate) {
        this.codeRunnerService = codeRunnerService;
        this.messagingTemplate = messagingTemplate;
        logger.info("✅ TerminalStompController initialized");
    }

    // Helpers to send standardized messages to frontend via STOMP topic
    private void sendOutputMessage(String sessionId, String output) {
        try {
            var msg = objectMapper.createObjectNode();
            msg.put("type", "terminal-output");
            msg.put("output", output);
            messagingTemplate.convertAndSend("/topic/terminal/" + sessionId, msg.toString());
        } catch (Exception e) {
            logger.error("❌ Failed to send output message for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    private void sendErrorMessage(String sessionId, String error) {
        try {
            var msg = objectMapper.createObjectNode();
            msg.put("type", "terminal-error");
            msg.put("message", error);
            messagingTemplate.convertAndSend("/topic/terminal/" + sessionId, msg.toString());
        } catch (Exception e) {
            logger.error("❌ Failed to send error message for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    @MessageMapping("/terminal/{sessionId}")
    public void handleTerminalMessage(@DestinationVariable String sessionId, String payload) {
        logger.info("📨 Received message for session: {}", sessionId);
        logger.debug("📋 Raw payload: {}", payload);

        try {
            JsonNode node = objectMapper.readTree(payload);
            logger.info("📦 Parsed JSON - type: {}", node.has("type") ? node.get("type").asText() : "N/A");

            // Only handle terminal messages here
            if (!(node.has("type") && "terminal".equals(node.get("type").asText()))) {
                logger.debug("🔕 Not a terminal message, ignoring");
                return;
            }

            logger.info("🖥️ Processing terminal message");
            boolean startShell = node.path("startShell").asBoolean(false);
            String command = node.path("command").asText("");

            logger.info("🔍 startShell: {}, command: '{}'", startShell, command);

            if (startShell) {
                logger.info("🚀 Starting new interactive shell for session: {}", sessionId);

                // Create temp project folder and populate with files if provided
                Path projectFolder = Files.createTempDirectory("stomp-project-");
                logger.info("📁 Created project folder: {}", projectFolder);

                // If frontend provided projectFiles (map of relativePath -> content), write them into the temp folder
                if (node.has("projectFiles") && node.get("projectFiles").isObject()) {
                    JsonNode filesNode = node.get("projectFiles");
                    var fields = filesNode.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        String relative = entry.getKey();
                        String content = entry.getValue().asText("");
                        try {
                            Path dest = projectFolder.resolve(relative);
                            if (dest.getParent() != null) Files.createDirectories(dest.getParent());
                            Files.writeString(dest, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        } catch (Exception e) {
                            logger.warn("Failed to write project file {}: {}", relative, e.getMessage());
                        }
                    }
                }

                // Optionally copy files if frontend supplied a server-side path (not recommended)
                if (node.has("sourceDir") && !node.get("sourceDir").asText().isEmpty()) {
                    try {
                        Path source = Path.of(node.get("sourceDir").asText());
                        CodeRunnerService.copyDirectory(source, projectFolder);
                    } catch (Exception e) {
                        logger.warn("Failed to copy sourceDir: {}", e.getMessage());
                    }
                }

                // Start interactive shell
                try {
                    final var shell = codeRunnerService.startInteractiveShell(sessionId, projectFolder);
                    logger.info("✅ Shell process started for session: {}", sessionId);

                    // Send confirmation to frontend
                    sendOutputMessage(sessionId, "✅ Interactive shell started successfully!\n");
                    sendOutputMessage(sessionId, "📁 Working directory: /project\n");
                    sendOutputMessage(sessionId, "💡 Type your commands below:\n\n");

                    // Stream stdout
                    new Thread(() -> {
                        logger.info("🔄 Starting stdout stream thread for session: {}", sessionId);
                        try (BufferedReader reader = codeRunnerService.getShellOutput(sessionId)) {
                            if (reader == null) {
                                logger.error("❌ Shell output reader is null for session: {}", sessionId);
                                return;
                            }
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sendOutputMessage(sessionId, line + "\n");
                            }
                            logger.info("🏁 stdout stream ended for session: {}", sessionId);
                        } catch (Exception e) {
                            logger.error("❌ Error streaming shell stdout for session {}: {}", sessionId, e.getMessage(), e);
                            sendErrorMessage(sessionId, "Error reading shell output: " + e.getMessage());
                        }
                    }, "stdout-stream-" + sessionId).start();

                    // Stream stderr
                    new Thread(() -> {
                        logger.info("🔄 Starting stderr stream thread for session: {}", sessionId);
                        try (BufferedReader reader = codeRunnerService.getShellError(sessionId)) {
                            if (reader == null) return;
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sendOutputMessage(sessionId, "[ERROR] " + line + "\n");
                            }
                            logger.info("🏁 stderr stream ended for session: {}", sessionId);
                        } catch (Exception e) {
                            logger.error("❌ Error streaming shell stderr for session {}: {}", sessionId, e.getMessage(), e);
                        }
                    }, "stderr-stream-" + sessionId).start();

                    // If initial command given, send it
                    if (!command.isEmpty()) {
                        Thread.sleep(1000);
                        codeRunnerService.sendToShell(sessionId, command);
                    }

                } catch (Exception e) {
                    logger.error("❌ Failed to start shell for session {}: {}", sessionId, e.getMessage(), e);
                    sendErrorMessage(sessionId, "Failed to start shell: " + e.getMessage());
                }

                return;
            }

            // If not startShell, treat payload as a command to an existing shell
            if (!command.isEmpty()) {
                if (!codeRunnerService.isShellAlive(sessionId)) {
                    sendErrorMessage(sessionId, "No active shell session. Please start a shell first.");
                    return;
                }
                try {
                    codeRunnerService.sendToShell(sessionId, command);
                } catch (Exception e) {
                    logger.error("❌ Failed to send command to session {}: {}", sessionId, e.getMessage(), e);
                    sendErrorMessage(sessionId, "Failed to send command: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("❌ Error handling terminal message for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Internal server error: " + e.getMessage());
        }
    }
}
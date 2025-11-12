package com.codeColab.codab.Controllers;

import com.codeColab.codab.service.CodeRunnerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pty4j.PtyProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

/**
 * STOMP controller that accepts messages from frontend at /app/terminal/{sessionId}
 * and publishes output to /topic/terminal/{sessionId}.
 * Now uses JPty for terminal sessions.
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
                logger.info("🚀 Starting new interactive shell (JPty) for session: {}", sessionId);

                // Create temp project folder and populate with files if provided
                Path projectFolder = Files.createTempDirectory("stomp-project-");
                logger.info("📁 Created project folder: {}", projectFolder);

                // If frontend provided projectFiles (map of relativePath -> content), write them
                if (node.has("projectFiles") && node.get("projectFiles").isObject()) {
                    JsonNode filesNode = node.get("projectFiles");
                    var fields = filesNode.fields();
                    int fileCount = 0;
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        String relative = entry.getKey();
                        String content = entry.getValue().asText("");
                        try {
                            Path dest = projectFolder.resolve(relative);
                            if (dest.getParent() != null) Files.createDirectories(dest.getParent());
                            Files.writeString(dest, content, StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            fileCount++;
                        } catch (Exception e) {
                            logger.warn("Failed to write project file {}: {}", relative, e.getMessage());
                        }
                    }
                    logger.info("✅ Written {} project files", fileCount);
                }

                // Optionally copy files if frontend supplied a server-side path
                if (node.has("sourceDir") && !node.get("sourceDir").asText().isEmpty()) {
                    try {
                        Path source = Path.of(node.get("sourceDir").asText());
                        CodeRunnerService.copyDirectory(source, projectFolder);
                        logger.info("✅ Copied files from sourceDir");
                    } catch (Exception e) {
                        logger.warn("Failed to copy sourceDir: {}", e.getMessage());
                    }
                }

                // Start interactive shell using JPty
                try {
                    final PtyProcess ptyProcess = codeRunnerService.startInteractiveShell(sessionId, projectFolder);
                    logger.info("✅ PTY process started for session: {}", sessionId);

                    // Send confirmation to frontend
                    sendOutputMessage(sessionId, "✅ Interactive terminal started (JPty)!\n");
                    sendOutputMessage(sessionId, "📁 Working directory: " + projectFolder.toAbsolutePath() + "\n");
                    sendOutputMessage(sessionId, "💡 Type your commands below:\n\n");

                    // Stream PTY output
                    new Thread(() -> {
                        logger.info("🔄 Starting PTY output stream thread for session: {}", sessionId);
                        try {
                            InputStream stdout = codeRunnerService.getShellOutputStream(sessionId);
                            if (stdout == null) {
                                logger.error("❌ Shell output stream is null for session: {}", sessionId);
                                sendErrorMessage(sessionId, "Failed to get shell output stream");
                                return;
                            }

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            int totalBytes = 0;

                            while ((bytesRead = stdout.read(buffer)) != -1) {
                                totalBytes += bytesRead;
                                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                                logger.debug("📤 PTY OUTPUT [{}] {} bytes", sessionId, bytesRead);
                                sendOutputMessage(sessionId, output);
                            }

                            logger.info("🏁 PTY output stream ended for session: {} (total bytes: {})", sessionId, totalBytes);
                            sendOutputMessage(sessionId, "\n[Terminal session ended]\n");
                        } catch (Exception e) {
                            logger.error("❌ Error streaming PTY output for session {}: {}", sessionId, e.getMessage(), e);
                            sendErrorMessage(sessionId, "Error reading shell output: " + e.getMessage());
                        }
                    }, "stdout-stream-" + sessionId).start();

                    // Handle terminal resize if specified
                    if (node.has("cols") && node.has("rows")) {
                        int cols = node.get("cols").asInt(120);
                        int rows = node.get("rows").asInt(30);
                        codeRunnerService.resizeTerminal(sessionId, cols, rows);
                        logger.info("📐 Terminal resized to {}x{}", cols, rows);
                    }

                    // If initial command given, send it after delay
                    if (!command.isEmpty()) {
                        Thread.sleep(500);
                        codeRunnerService.sendToShell(sessionId, command);
                        logger.info("✅ Initial command sent");
                    }

                } catch (Exception e) {
                    logger.error("❌ Failed to start shell for session {}: {}", sessionId, e.getMessage(), e);
                    sendErrorMessage(sessionId, "Failed to start shell: " + e.getMessage());
                }

                return;
            }

            // Handle terminal resize
            if (node.has("resize") && node.get("resize").asBoolean()) {
                int cols = node.path("cols").asInt(120);
                int rows = node.path("rows").asInt(30);
                logger.info("📐 Resizing terminal: {}x{}", cols, rows);
                codeRunnerService.resizeTerminal(sessionId, cols, rows);
                return;
            }

            // If not startShell or resize, treat payload as a command to an existing shell
            if (!command.isEmpty()) {
                if (!codeRunnerService.isShellAlive(sessionId)) {
                    sendErrorMessage(sessionId, "No active shell session. Please start a shell first.");
                    return;
                }
                try {
                    codeRunnerService.sendToShell(sessionId, command);
                    logger.info("✅ Command sent to shell");
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
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
import com.pty4j.PtyProcess;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public class TerminalWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CodeRunnerService codeRunnerService;
    private final ConcurrentHashMap<String, OutputStream> sessionInputs = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(CodeRunnerService codeRunnerService) {
        this.codeRunnerService = codeRunnerService;
        logger.info("✅ TerminalWebSocketHandler initialized");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("🔌 WebSocket connection established - sessionId: {}", session.getId());

        try {
            session.sendMessage(new TextMessage("✅ Terminal WebSocket ready!\n"));
            session.sendMessage(new TextMessage("📡 Session ID: " + session.getId() + "\n"));
            logger.info("✅ Welcome message sent to session: {}", session.getId());
        } catch (Exception e) {
            logger.error("❌ Error sending welcome message: {}", e.getMessage());
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        logger.info("📨 WebSocket message received - sessionId: {}", sessionId);
        logger.debug("📋 Raw payload: {}", message.getPayload());

        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            logger.info("📦 Parsed JSON - type: {}", node.has("type") ? node.get("type").asText() : "N/A");

            // --- Handle interactive terminal messages from frontend (JPty) ---
            if (node.has("type") && "terminal".equals(node.get("type").asText())) {
                logger.info("🖥️ Processing terminal message");

                String command = node.has("command") ? node.get("command").asText() : "";
                boolean startShell = node.has("startShell") && node.get("startShell").asBoolean();

                logger.info("🔍 startShell: {}, command: '{}'", startShell, command);

                if (startShell) {
                    logger.info("🚀 Starting new interactive shell (JPty)");

                    // Determine project folder
                    Path projectFolder;
                    if (node.has("projectPath") && !node.get("projectPath").asText().isEmpty()) {
                        projectFolder = Paths.get(node.get("projectPath").asText());
                        logger.info("📂 Using provided projectPath: {}", projectFolder);
                    } else if (node.has("projectZip")) {
                        // If zipped project is provided, unpack to a temp folder
                        projectFolder = Files.createTempDirectory("project-unzip-");
                        logger.info("📦 Created folder for unzipped project: {}", projectFolder);
                        // TODO: implement unzip logic if frontend sends zipped project bytes/base64
                        logger.warn("⚠️ Project zip handling not yet implemented");
                    } else {
                        projectFolder = Files.createTempDirectory("terminal-project-");
                        logger.info("📁 Created temp project folder: {}", projectFolder);
                    }

                    // Copy files if sourceDir is provided
                    if (node.has("sourceDir") && !node.get("sourceDir").asText().isEmpty()) {
                        try {
                            Path sourceDir = Paths.get(node.get("sourceDir").asText());
                            logger.info("📂 Copying files from sourceDir: {}", sourceDir);
                            CodeRunnerService.copyDirectory(sourceDir, projectFolder);
                            logger.info("✅ Files copied successfully");
                        } catch (Exception e) {
                            logger.error("❌ Failed to copy sourceDir: {}", e.getMessage(), e);
                            session.sendMessage(new TextMessage("⚠️ Warning: Failed to copy source directory\n"));
                        }
                    }

                    // If frontend provided projectFiles (map of relativePath -> content), write them
                    if (node.has("projectFiles") && node.get("projectFiles").isObject()) {
                        try {
                            logger.info("📥 Writing projectFiles into project folder");
                            Iterator<Map.Entry<String, JsonNode>> fields = node.get("projectFiles").fields();
                            while (fields.hasNext()) {
                                Map.Entry<String, JsonNode> entry = fields.next();
                                String relPath = entry.getKey();
                                String content = entry.getValue().asText("");
                                Path target = projectFolder.resolve(relPath);
                                if (target.getParent() != null) {
                                    Files.createDirectories(target.getParent());
                                }
                                Files.writeString(target, content, StandardCharsets.UTF_8,
                                        java.nio.file.StandardOpenOption.CREATE,
                                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                            }
                            logger.info("✅ Written {} files into {}", node.get("projectFiles").size(), projectFolder);
                        } catch (Exception e) {
                            logger.error("❌ Failed to write projectFiles: {}", e.getMessage(), e);
                            session.sendMessage(new TextMessage("⚠️ Warning: Failed to write project files: " + e.getMessage() + "\n"));
                        }
                    }

                    // Start the interactive shell using JPty
                    try {
                        PtyProcess ptyProcess = codeRunnerService.startInteractiveShell(sessionId, projectFolder);

                        if (Objects.nonNull(ptyProcess) && ptyProcess.getOutputStream() != null) {
                            sessionInputs.put(sessionId, ptyProcess.getOutputStream());
                            logger.info("✅ Stored PTY output stream for session: {}", sessionId);
                        } else {
                            logger.error("❌ PTY process or output stream is null");
                            session.sendMessage(new TextMessage("❌ Failed to start shell\n"));
                            return;
                        }

                        // Send welcome messages
                        session.sendMessage(new TextMessage("✅ Interactive terminal started (JPty)\n"));
                        session.sendMessage(new TextMessage("📁 Working directory: " + projectFolder.toAbsolutePath() + "\n"));
                        session.sendMessage(new TextMessage("💡 Type your commands below:\n\n"));
                        logger.info("✅ Welcome messages sent");

                        // Start thread to stream PTY output back to the frontend
                        new Thread(() -> {
                            logger.info("🔄 Starting PTY output stream thread for session: {}", sessionId);
                            try {
                                InputStream stdout = codeRunnerService.getShellOutputStream(sessionId);
                                if (stdout == null) {
                                    logger.error("❌ Shell output stream is null");
                                    return;
                                }

                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                int totalBytes = 0;

                                while ((bytesRead = stdout.read(buffer)) != -1) {
                                    totalBytes += bytesRead;
                                    String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                                    logger.debug("📤 PTY OUTPUT [{}] {} bytes: {}", sessionId, bytesRead,
                                            output.length() > 100 ? output.substring(0, 100) + "..." : output);

                                    try {
                                        session.sendMessage(new TextMessage(output));
                                    } catch (Exception e) {
                                        logger.error("❌ Error sending message to WebSocket: {}", e.getMessage());
                                        break;
                                    }
                                }

                                logger.info("🏁 PTY output stream ended for session: {} (total bytes: {})", sessionId, totalBytes);
                                session.sendMessage(new TextMessage("\n[Terminal session ended]\n"));
                            } catch (Exception e) {
                                logger.error("❌ Error streaming PTY output: {}", e.getMessage(), e);
                            }
                        }, "ws-pty-output-" + sessionId).start();

                        // Handle terminal resize events
                        if (node.has("cols") && node.has("rows")) {
                            int cols = node.get("cols").asInt(120);
                            int rows = node.get("rows").asInt(30);
                            codeRunnerService.resizeTerminal(sessionId, cols, rows);
                            logger.info("📐 Terminal resized to {}x{}", cols, rows);
                        }

                        // If an initial command is provided, send it after a brief delay
                        if (!command.isEmpty()) {
                            logger.info("⏱️ Waiting 500ms before sending initial command...");
                            Thread.sleep(500); // Give shell time to initialize
                            logger.info("📨 Sending initial command: '{}'", command);
                            codeRunnerService.sendToShell(sessionId, command);
                        }

                    } catch (Exception e) {
                        logger.error("❌ Failed to start shell: {}", e.getMessage(), e);
                        session.sendMessage(new TextMessage("❌ Error starting shell: " + e.getMessage() + "\n"));
                    }

                } else if (node.has("resize") && node.get("resize").asBoolean()) {
                    // Handle terminal resize
                    int cols = node.path("cols").asInt(120);
                    int rows = node.path("rows").asInt(30);
                    logger.info("📐 Resizing terminal: {}x{}", cols, rows);
                    codeRunnerService.resizeTerminal(sessionId, cols, rows);

                } else if (!command.isEmpty()) {
                    // Send command to existing shell
                    logger.info("📨 Sending command to existing shell: '{}'", command);

                    if (!codeRunnerService.isShellAlive(sessionId)) {
                        logger.error("❌ No active shell found for session: {}", sessionId);
                        session.sendMessage(new TextMessage("❌ No active shell session. Please start a shell first.\n"));
                        return;
                    }

                    try {
                        codeRunnerService.sendToShell(sessionId, command);
                        logger.info("✅ Command sent successfully");
                    } catch (Exception e) {
                        logger.error("❌ Failed to send command: {}", e.getMessage(), e);
                        session.sendMessage(new TextMessage("❌ Error sending command: " + e.getMessage() + "\n"));
                    }
                }

                return;
            }

            // Non-terminal messages are treated as single-file code execution requests (Docker-based)
            logger.info("💻 Processing code execution request (Docker)");

            String code = node.has("code") ? node.get("code").asText() : "";
            String fileName = node.has("fileName") ? node.get("fileName").asText() : "Main.txt";
            String language = getLanguageFromFileName(fileName);
            String input = node.has("input") ? node.get("input").asText() : "";

            logger.info("📄 fileName: {}, language: {}, hasInput: {}", fileName, language, !input.isEmpty());

            session.sendMessage(new TextMessage("▶️ Running code in Docker...\n"));

            Process process = codeRunnerService.startProcess(code, language, input, fileName);

            if (process != null && process.getOutputStream() != null) {
                sessionInputs.put(session.getId(), process.getOutputStream());
                logger.info("✅ Process started and output stream stored");
            } else {
                logger.error("❌ Failed to start process");
                session.sendMessage(new TextMessage("❌ Failed to start code execution\n"));
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
                        logger.info("📤 CODE EXECUTION OUTPUT Line {}: {}", lineCount, line);
                        session.sendMessage(new TextMessage(line + "\n"));
                    }
                    session.sendMessage(new TextMessage("\n[Process completed]\n"));
                    logger.info("🏁 Code execution completed (total lines: {})", lineCount);
                } catch (Exception e) {
                    logger.error("❌ Error streaming process output: {}", e.getMessage(), e);
                }
            }, "code-exec-" + sessionId).start();

        } catch (Exception e) {
            logger.error("❌ Error in handleTextMessage: {}", e.getMessage(), e);
            try {
                session.sendMessage(new TextMessage("❌ Error: " + e.getMessage() + "\n"));
            } catch (Exception ignored) {
                logger.error("❌ Failed to send error message to client");
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        logger.info("🔌 WebSocket connection closed - sessionId: {}, status: {}", sessionId, status);

        // Close any interactive shell tied to this session
        try {
            if (codeRunnerService.isShellAlive(sessionId)) {
                logger.info("🛑 Closing shell for session: {}", sessionId);
                codeRunnerService.closeShell(sessionId);
            }
        } catch (Exception e) {
            logger.error("❌ Error closing shell: {}", e.getMessage());
        }

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
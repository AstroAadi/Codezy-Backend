package com.codeColab.codab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodeRunnerService {
    private static final Logger logger = LoggerFactory.getLogger(CodeRunnerService.class);

    // Store terminal sessions with their associated PTY processes
    private final Map<String, TerminalSession> terminalSessions = new ConcurrentHashMap<>();

    // Inner class to hold PTY session data
    private static class TerminalSession {
        PtyProcess ptyProcess;
        OutputStream stdin;
        InputStream stdout;
        String workingDirectory;
        Thread outputThread;
        Thread errorThread;

        TerminalSession(PtyProcess ptyProcess, String workingDirectory) {
            this.ptyProcess = ptyProcess;
            this.stdin = ptyProcess.getOutputStream();
            this.stdout = ptyProcess.getInputStream();
            this.workingDirectory = workingDirectory;
        }
    }

    // ==================== CODE EXECUTION METHODS (Docker-based, unchanged) ====================

    public String runCode(String code, String language, String input, String fileName) {
        logger.info("🎯 runCode called - fileName: {}, language: {}, hasInput: {}", fileName, language, input != null && !input.isEmpty());

        try {
            if (!isDockerAvailable()) {
                logger.warn("⚠️ Docker is not available");
                return "⚠️ Docker is not available in this environment. Code execution is disabled.";
            }

            String folder = Files.createTempDirectory("code-").toFile().getAbsolutePath();
            logger.info("📁 Created temp folder: {}", folder);

            Path filePath = Path.of(folder, fileName);
            Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));
            logger.info("📝 Written code to file: {}", filePath);

            String result = executeInDocker(fileName, language, folder, code, input);
            logger.info("✅ Code execution completed");
            return result;
        } catch (Exception e) {
            logger.error("❌ Error in runCode: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            int exitCode = process.waitFor();
            logger.info("🐳 Docker available check: exit code {}", exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            logger.error("❌ Docker check failed: {}", e.getMessage());
            return false;
        }
    }

    private String executeInDocker(String fileName, String language, String folder, String code, String input) throws IOException, InterruptedException {
        Path filePath = Path.of(folder, fileName);
        Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));

        String runScript = "#!/bin/bash\n" +
                "echo \"Starting execution for $1\"\n" +
                "case \"$1\" in\n" +
                "    *.py) python3 $1 ; exit $? ;;\n" +
                "    *.js) node $1 ; exit $? ;;\n" +
                "    *.java) javac $1 && java ${1%.*} ; exit $? ;;\n" +
                "    *.c) gcc $1 -o ${1%.*} && ./${1%.*} ; exit $? ;;\n" +
                "    *) echo \"Unsupported language\" ; exit 1 ;;\n" +
                "esac";

        Files.writeString(Path.of(folder, "run_code.sh"), runScript, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("📜 Created run_code.sh script");

        String dockerfile = "FROM ubuntu:22.04\n" +
                "ENV DEBIAN_FRONTEND=noninteractive\n" +
                "RUN apt-get update && apt-get install -y \\\n" +
                "    python3 \\\n" +
                "    python3-pip \\\n" +
                "    openjdk-17-jdk \\\n" +
                "    gcc \\\n" +
                "    nodejs \\\n" +
                "    npm \\\n" +
                "    curl \\\n" +
                "    nano \\\n" +
                "    && apt-get clean\n" +
                "WORKDIR /app\n" +
                "COPY run_code.sh /app/\n" +
                "RUN chmod +x /app/run_code.sh\n";
        dockerfile += "COPY " + fileName + " /app/" + fileName + "\n";
        dockerfile += "ENTRYPOINT [\"/app/run_code.sh\"]\n";

        Files.writeString(Path.of(folder, "Dockerfile"), dockerfile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("🐳 Created Dockerfile");

        Process build = new ProcessBuilder("docker", "build", "-t", "code-runner", folder).redirectErrorStream(true).start();
        String buildOutput = new String(build.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        logger.info("🔨 Docker build output: {}", buildOutput);

        int buildExitCode = build.waitFor();
        if (buildExitCode != 0) {
            logger.error("❌ Docker build failed with exit code: {}", buildExitCode);
            return "Error building Docker image: " + buildOutput;
        }

        List<String> runCmd = new ArrayList<>(Arrays.asList(
                "docker", "run", "--rm", "-i", "code-runner", fileName
        ));
        logger.info("🚀 Running Docker container with command: {}", String.join(" ", runCmd));

        Process run = new ProcessBuilder(runCmd).redirectErrorStream(true).start();

        if (input != null && !input.isEmpty()) {
            try (OutputStream stdin = run.getOutputStream()) {
                stdin.write((input + "\n").getBytes(StandardCharsets.UTF_8));
                stdin.flush();
                logger.info("⌨️ Wrote input to Docker container: {}", input);
            }
        }

        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int runExitCode = run.waitFor();

        logger.info("🏁 Docker run exited with code: {}", runExitCode);

        if (runExitCode != 0) {
            if (output.isEmpty()) {
                output = "Error executing code. Exit code: " + runExitCode;
            } else {
                output = output + "\n[Process exited with code: " + runExitCode + "]";
            }
        }

        try {
            Files.walk(Path.of(folder)).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            logger.info("🧹 Cleaned up temporary directory: {}", folder);
        } catch (Exception e) {
            logger.error("⚠️ Error cleaning up temporary files: {}", e.getMessage());
        }

        logger.info("📤 Code execution output: {}", output);
        return output;
    }

    public Process runCodeInteractive(String code, String language, String fileName, java.util.function.Consumer<String> outputConsumer) throws IOException, InterruptedException {
        logger.info("🎯 runCodeInteractive called - fileName: {}, language: {}", fileName, language);

        String folder = Files.createTempDirectory("code-").toFile().getAbsolutePath();
        Path filePath = Path.of(folder, fileName);
        Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));

        String runScript = "#!/bin/bash\n" +
                "echo \"Starting execution for $1\"\n" +
                "case \"$1\" in\n" +
                "    *.py) python3 $1 ; exit $? ;;\n" +
                "    *.js) node $1 ; exit $? ;;\n" +
                "    *.java) javac $1 && java ${1%.*} ; exit $? ;;\n" +
                "    *.c) gcc $1 -o ${1%.*} && ./${1%.*} ; exit $? ;;\n" +
                "    *) echo \"Unsupported language\" ; exit 1 ;;\n" +
                "esac";

        Files.writeString(Path.of(folder, "run_code.sh"), runScript, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String dockerfile = "FROM ubuntu:22.04\n" +
                "ENV DEBIAN_FRONTEND=noninteractive\n" +
                "RUN apt-get update && apt-get install -y \\\n" +
                "    python3 \\\n" +
                "    python3-pip \\\n" +
                "    openjdk-17-jdk \\\n" +
                "    gcc \\\n" +
                "    nodejs \\\n" +
                "    npm \\\n" +
                "    curl \\\n" +
                "    nano \\\n" +
                "    && apt-get clean\n" +
                "WORKDIR /app\n" +
                "COPY run_code.sh /app/\n" +
                "RUN chmod +x /app/run_code.sh\n";
        dockerfile += "COPY " + fileName + " /app/" + fileName + "\n";
        dockerfile += "ENTRYPOINT [\"/app/run_code.sh\"]\n";

        Files.writeString(Path.of(folder, "Dockerfile"), dockerfile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Process build = new ProcessBuilder("docker", "build", "-t", "code-runner", folder).redirectErrorStream(true).start();
        try (BufferedReader buildReader = new BufferedReader(new InputStreamReader(build.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = buildReader.readLine()) != null) {
                logger.debug("🔨 Build: {}", line);
            }
        }

        int buildExitCode = build.waitFor();
        if (buildExitCode != 0) {
            logger.error("❌ Error building Docker image");
            outputConsumer.accept("Error building Docker image\n");
            return null;
        }

        List<String> runCmd = new ArrayList<>(Arrays.asList(
                "docker", "run", "--rm", "-i", "code-runner", fileName
        ));
        Process run = new ProcessBuilder(runCmd).redirectErrorStream(true).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(run.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputConsumer.accept(line + "\n");
                }
            } catch (IOException e) {
                logger.error("❌ Error reading process output: {}", e.getMessage());
                outputConsumer.accept("[Error reading process output]\n");
            }
        }).start();

        return run;
    }

    public Process startProcess(String code, String language, String input, String fileName) {
        logger.info("🎯 startProcess called - fileName: {}, language: {}", fileName, language);

        try {
            String folder = Files.createTempDirectory("code-").toFile().getAbsolutePath();
            Path filePath = Path.of(folder, fileName);
            Files.write(filePath, code.getBytes(StandardCharsets.UTF_8));

            String runScript = "#!/bin/bash\n" +
                    "echo \"Starting execution for $1\"\n" +
                    "case \"$1\" in\n" +
                    "    *.py) python3 $1 ; exit $? ;;\n" +
                    "    *.js) node $1 ; exit $? ;;\n" +
                    "    *.java) javac $1 && java ${1%.*} ; exit $? ;;\n" +
                    "    *.c) gcc $1 -o ${1%.*} && ./${1%.*} ; exit $? ;;\n" +
                    "    *) echo \"Unsupported language\" ; exit 1 ;;\n" +
                    "esac";

            Files.writeString(Path.of(folder, "run_code.sh"), runScript, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String dockerfile = "FROM ubuntu:22.04\n" +
                    "ENV DEBIAN_FRONTEND=noninteractive\n" +
                    "RUN apt-get update && apt-get install -y \\\n" +
                    "    python3 \\\n" +
                    "    python3-pip \\\n" +
                    "    openjdk-17-jdk \\\n" +
                    "    gcc \\\n" +
                    "    nodejs \\\n" +
                    "    npm \\\n" +
                    "    curl \\\n" +
                    "    nano \\\n" +
                    "    && apt-get clean\n" +
                    "WORKDIR /app\n" +
                    "COPY run_code.sh /app/\n" +
                    "RUN chmod +x /app/run_code.sh\n";
            dockerfile += "COPY " + fileName + " /app/" + fileName + "\n";
            dockerfile += "ENTRYPOINT [\"/app/run_code.sh\"]\n";

            Files.writeString(Path.of(folder, "Dockerfile"), dockerfile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Process build = new ProcessBuilder("docker", "build", "-t", "code-runner", folder).redirectErrorStream(true).start();
            build.waitFor();

            List<String> runCmd = new ArrayList<>(Arrays.asList(
                    "docker", "run", "--rm", "-i", "code-runner", fileName
            ));
            Process run = new ProcessBuilder(runCmd).redirectErrorStream(true).start();

            if (input != null && !input.isEmpty()) {
                try (OutputStream stdin = run.getOutputStream()) {
                    stdin.write((input + "\n").getBytes(StandardCharsets.UTF_8));
                    stdin.flush();
                    logger.info("⌨️ Wrote input to process: {}", input);
                }
            }

            return run;
        } catch (Exception e) {
            logger.error("❌ Failed to start process: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start process: " + e.getMessage(), e);
        }
    }

    // ==================== TERMINAL METHODS (JPty-based, NEW) ====================

    /**
     * Start an interactive shell using JPty for a project directory.
     * This creates a native PTY with proper terminal support.
     */
    public PtyProcess startInteractiveShell(String sessionId, Path projectFolder) {
        logger.info("🎯 startInteractiveShell (JPty) called - sessionId: {}, projectFolder: {}", sessionId, projectFolder);

        try {
            String workingDir = projectFolder.toAbsolutePath().toString();

            // Ensure the working directory exists
            if (!Files.exists(projectFolder)) {
                Files.createDirectories(projectFolder);
                logger.info("📁 Created project folder: {}", projectFolder);
            }

            // Determine the shell command based on OS
            String[] command;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows: use cmd.exe or PowerShell
                command = new String[]{"cmd.exe"};
                logger.info("🪟 Detected Windows OS, using cmd.exe");
            } else {
                // Unix/Linux/Mac: use bash
                command = new String[]{"/bin/bash", "-l"};
                logger.info("🐧 Detected Unix-like OS, using bash");
            }

            // Configure environment variables
            Map<String, String> envVars = new HashMap<>(System.getenv());
            envVars.put("TERM", "xterm-256color");
            envVars.put("PS1", "\\u@\\h:\\w$ "); // Custom prompt

            logger.info("🔧 Environment variables configured");

            // Build the PTY process
            PtyProcessBuilder builder = new PtyProcessBuilder(command)
                    .setDirectory(workingDir)
                    .setEnvironment(envVars)
                    .setInitialColumns(120)
                    .setInitialRows(30)
                    .setConsole(false)
                    .setCygwin(false);

            logger.info("🔨 Building PTY process with command: {}", Arrays.toString(command));

            // Start the PTY process
            PtyProcess ptyProcess = builder.start();
            logger.info("✅ PTY process started successfully");

            // Create and store terminal session
            TerminalSession session = new TerminalSession(ptyProcess, workingDir);
            terminalSessions.put(sessionId, session);
            logger.info("💾 Stored terminal session for sessionId: {}", sessionId);

            // Give the shell a moment to initialize
            Thread.sleep(200);

            logger.info("✅ Interactive shell (JPty) started for session: {}", sessionId);
            return ptyProcess;

        } catch (Exception e) {
            logger.error("❌ Failed to start interactive shell (JPty) for session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to start interactive shell: " + e.getMessage(), e);
        }
    }

    /**
     * Send a command to an active PTY shell session
     */
    public void sendToShell(String sessionId, String command) throws IOException {
        logger.info("📨 sendToShell - sessionId: {}, command: '{}'", sessionId, command);

        TerminalSession session = terminalSessions.get(sessionId);
        if (session == null) {
            logger.error("❌ No terminal session found for sessionId: {}", sessionId);
            throw new IOException("No terminal session found for sessionId: " + sessionId);
        }

        if (session.stdin == null) {
            logger.error("❌ stdin is null for sessionId: {}", sessionId);
            throw new IOException("Shell stdin is not available");
        }

        try {
            // Write command with newline
            byte[] commandBytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
            session.stdin.write(commandBytes);
            session.stdin.flush();
            logger.info("✅ Command sent successfully to session: {}", sessionId);
        } catch (IOException e) {
            logger.error("❌ Error sending command to session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the output stream (for reading) for a shell session
     */
    public InputStream getShellOutputStream(String sessionId) {
        logger.debug("📖 getShellOutputStream - sessionId: {}", sessionId);

        TerminalSession session = terminalSessions.get(sessionId);
        if (session != null) {
            return session.stdout;
        }

        logger.warn("⚠️ No session found for sessionId: {}", sessionId);
        return null;
    }

    /**
     * Get a BufferedReader for shell output (backward compatibility)
     */
    public BufferedReader getShellOutput(String sessionId) {
        logger.debug("📖 getShellOutput - sessionId: {}", sessionId);

        InputStream inputStream = getShellOutputStream(sessionId);
        if (inputStream != null) {
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        logger.warn("⚠️ No input stream found for sessionId: {}", sessionId);
        return null;
    }

    /**
     * Get the error reader for a shell session (PTY doesn't separate stderr)
     */
    public BufferedReader getShellError(String sessionId) {
        logger.debug("📖 getShellError - sessionId: {}", sessionId);

        // PTY combines stdout and stderr, so return null
        // This maintains backward compatibility
        return null;
    }

    /**
     * Check if a shell session exists and is alive
     */
    public boolean isShellAlive(String sessionId) {
        TerminalSession session = terminalSessions.get(sessionId);
        boolean alive = session != null && session.ptyProcess != null && session.ptyProcess.isAlive();
        logger.debug("🔍 isShellAlive - sessionId: {}, alive: {}", sessionId, alive);
        return alive;
    }

    /**
     * Resize the terminal window
     */
    public void resizeTerminal(String sessionId, int cols, int rows) {
        logger.info("📐 resizeTerminal - sessionId: {}, cols: {}, rows: {}", sessionId, cols, rows);

        TerminalSession session = terminalSessions.get(sessionId);
        if (session != null && session.ptyProcess != null) {
            try {
                session.ptyProcess.setWinSize(new WinSize(cols, rows));
                logger.info("✅ Terminal resized successfully");
            } catch (Exception e) {
                logger.error("❌ Failed to resize terminal: {}", e.getMessage(), e);
            }
        } else {
            logger.warn("⚠️ No session found for resize request: {}", sessionId);
        }
    }

    /**
     * Close and cleanup a shell session
     */
    public void closeShell(String sessionId) {
        logger.info("🔒 closeShell - sessionId: {}", sessionId);

        TerminalSession session = terminalSessions.get(sessionId);
        if (session != null) {
            try {
                // Stop output threads if any
                if (session.outputThread != null && session.outputThread.isAlive()) {
                    session.outputThread.interrupt();
                }
                if (session.errorThread != null && session.errorThread.isAlive()) {
                    session.errorThread.interrupt();
                }

                // Close streams
                if (session.stdin != null) {
                    session.stdin.close();
                }
                if (session.stdout != null) {
                    session.stdout.close();
                }

                // Destroy PTY process
                if (session.ptyProcess != null && session.ptyProcess.isAlive()) {
                    session.ptyProcess.destroy();
                    logger.info("🛑 PTY process destroyed for session: {}", sessionId);
                }
            } catch (Exception e) {
                logger.error("⚠️ Error closing shell session {}: {}", sessionId, e.getMessage());
            }

            terminalSessions.remove(sessionId);
            logger.info("✅ Shell session closed and removed: {}", sessionId);
        } else {
            logger.warn("⚠️ No session to close for sessionId: {}", sessionId);
        }
    }

    /**
     * Utility to copy all files from a source directory to a destination directory.
     */
    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path dest = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    if (!Files.exists(dest)) {
                        Files.createDirectories(dest);
                    }
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Get all active session IDs
     */
    public Set<String> getActiveSessionIds() {
        return new HashSet<>(terminalSessions.keySet());
    }
}
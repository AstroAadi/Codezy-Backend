package com.codeColab.codab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class CodeRunnerService {
    private static final Logger logger = LoggerFactory.getLogger(CodeRunnerService.class);

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

    // ==================== TERMINAL METHODS (Removed - Using WebContainers in Frontend) ====================

    /**
     * Terminal functionality is now handled entirely in the frontend using WebContainers.
     * No backend terminal sessions needed.
     *
     * Backend only handles:
     * - Code execution in Docker (above methods)
     * - File storage/retrieval (if needed)
     */
}
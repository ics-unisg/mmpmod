package ch.unisg.mmpmod.ambiguityresolver.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class MLServiceImpl implements MLService {

    private static final Logger logger = LoggerFactory.getLogger(MLServiceImpl.class);

    private final ConfigLoader mlConfig;
    private final ObjectMapper objectMapper;
    private final Path pythonExecutable;
    private final Path pythonScript;
    private final double confidenceThreshold;

    public MLServiceImpl() {
        this(new ConfigLoader(), new ObjectMapper());
    }

    public MLServiceImpl(ConfigLoader mlConfig) {
        this(mlConfig, new ObjectMapper());
    }

    MLServiceImpl(ConfigLoader mlConfig, ObjectMapper objectMapper) {
        this.mlConfig = mlConfig;
        this.objectMapper = objectMapper;
        this.pythonExecutable = resolveConfiguredPath("ml.pythonExecutable", true);
        this.pythonScript = resolveConfiguredPath("ml.pythonScript", false);
        this.confidenceThreshold = resolveConfidenceThreshold();
    }

    private double resolveConfidenceThreshold() {
        String configured = mlConfig.get("ml.confidenceThreshold");
        if (configured == null) {
            return 0.80d;
        }
        try {
            return Double.parseDouble(configured);
        } catch (NumberFormatException ex) {
            logger.warn("Invalid confidence threshold '{}'; falling back to default.", configured);
            return 0.80d;
        }
    }

    private Path resolveConfiguredPath(String propertyKey, boolean requireExecutable) {
        String configuredPath = mlConfig.get(propertyKey);
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException("Missing configuration for key: " + propertyKey);
        }

        Path path = Paths.get(configuredPath);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path).normalize();
        }

        if (!Files.exists(path)) {
            throw new IllegalStateException("Configured path does not exist for key " + propertyKey + ": " + path);
        }

        if (requireExecutable && !Files.isExecutable(path)) {
            throw new IllegalStateException(
                    "Configured executable is not executable for key " + propertyKey + ": " + path);
        }

        return path;
    }

    @Override
    public String analyzeFrames(List<String> framePaths) throws Exception {
        if (framePaths == null || framePaths.isEmpty()) {
            throw new IllegalArgumentException("Frame paths must not be empty");
        }

        List<String> command = new ArrayList<>();
        command.add(pythonExecutable.toString());
        command.add(pythonScript.toString());
        command.addAll(framePaths);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(pythonScript.getParent().toFile());

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Python process exited with code " + exitCode + ": " + output);
        }

        if (output.length() == 0) {
            logger.warn("ML inference script returned no output for frame paths: {}", framePaths);
            return null;
        }

        ObjectNode result = objectMapper.readValue(output.toString(), ObjectNode.class);
        System.out.println("ML output: " + output.toString());

        double confidence = result.path("confidence").asDouble(Double.NaN);
        if (Double.isNaN(confidence)) {
            logger.warn("ML response did not contain a confidence value. Response: {}", output);
        }
        boolean resolved = !Double.isNaN(confidence) && confidence >= confidenceThreshold;
        result.put("resolved_ambiguity", resolved);

        return objectMapper.writeValueAsString(result);
    }
}
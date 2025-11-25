package ch.unisg.mmpmod.ambiguityresolver.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CameraClient is a Python script client to interact with the camera service.
 */
@Service
@Primary
public class CameraServiceImpl implements CameraService {

    private static final Logger logger = LoggerFactory.getLogger(CameraServiceImpl.class);
    @Autowired
    private final CameraConfigLoader cameraConfig;

    private final Path pythonExecutable;
    private final Path pythonScript;
    private Process pythonProcess;
    private BufferedWriter pythonInput;
    private BufferedReader pythonOutput;
    private boolean shutdownHookRegistered;

    public CameraServiceImpl(CameraConfigLoader cameraConfig) {
        this.cameraConfig = cameraConfig;
        this.pythonExecutable = resolveConfiguredPath("cameraControl.pythonExecutable", true);
        this.pythonScript = resolveConfiguredPath("cameraControl.pythonScript", false);
    }
    public CameraServiceImpl() {
        this.cameraConfig = new CameraConfigLoader();
        this.pythonExecutable = resolveConfiguredPath("cameraControl.pythonExecutable", true);
        this.pythonScript = resolveConfiguredPath("cameraControl.pythonScript", false);
    }

    private Path resolveConfiguredPath(String propertyKey, boolean requireExecutable) {
        String configuredPath = cameraConfig.get(propertyKey);
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
            throw new IllegalStateException("Configured executable is not executable for key " + propertyKey + ": " + path);
        }

        return path;
    }

    private synchronized void ensurePythonProcess() throws IOException {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            return;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable.toString(),
                pythonScript.toString()
        );
        processBuilder.redirectErrorStream(true);

        pythonProcess = processBuilder.start();
        pythonInput = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
        pythonOutput = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));

        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownPythonProcess));
            shutdownHookRegistered = true;
        }
    }

    private synchronized String runPythonCommand(String command) throws Exception {
        ensurePythonProcess();

        pythonInput.write(command);
        pythonInput.newLine();
        pythonInput.flush();

        String line;
        while ((line = pythonOutput.readLine()) != null) {
            if (line.startsWith("OK")) {
                if ("OK".equals(line)) {
                    return "";
                }
                if (line.startsWith("OK:")) {
                    return line.substring(3);
                }
                return line;
            }
            if (line.startsWith("ERROR:")) {
                throw new RuntimeException("Python command '" + command + "' failed: " + line.substring(6));
            }

            // Treat any other lines as log output from the Python process
            logger.info("[python] {}", line);
        }

        throw new IllegalStateException("Python process terminated unexpectedly while handling command: " + command);
    }

    public String startCamera() throws Exception {
        return runPythonCommand("start");
    }
    public String stopCamera() throws Exception {
        return runPythonCommand("stop");
    }
    public String captureFrame() throws Exception {
        return runPythonCommand("capture");
    }
    private void waitBeforeNextFrame (long waitingTime) {
        try {
            Thread.sleep(waitingTime); // Wait for 500 milliseconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // re-interrupt the thread
            logger.error("Thread interrupted while waiting for next frame: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getFrames() throws Exception {
        boolean started = false;
        try {
            startCamera();
            started = true;
            Thread.sleep(300);

            List<String> filepaths = new ArrayList<>();
            int numberOfFrames = Integer.parseInt(cameraConfig.get("cameraControl.numberOfFrames"));
            long waitingTime = Long.parseLong(cameraConfig.get("cameraControl.waitingTime"));

            for (int i = 0; i < numberOfFrames; i++) {
                String filepath = captureFrame();
                if (filepath == null || filepath.isBlank()) {
                    logger.warn("Capture attempt {} did not return a valid filepath.", i + 1);
                    continue;
                }
                filepaths.add(filepath);
                if (i < numberOfFrames - 1) {
                    waitBeforeNextFrame(waitingTime);
                }
            }
            return filepaths;
        } finally {
            if (started) {
                stopCamera();
            }
        }
    }

    private synchronized void shutdownPythonProcess() {
        if (pythonProcess == null) {
            return;
        }

        try {
            if (pythonInput != null) {
                pythonInput.write("exit");
                pythonInput.newLine();
                pythonInput.flush();
            }
        } catch (IOException e) {
            logger.warn("Failed to send exit command to python process: {}", e.getMessage());
        } finally {
            try {
                if (pythonInput != null) {
                    pythonInput.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (pythonOutput != null) {
                    pythonOutput.close();
                }
            } catch (IOException ignored) {
            }

            pythonProcess.destroy();
            try {
                pythonProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for python process to terminate.");
            }

            pythonProcess = null;
            pythonInput = null;
            pythonOutput = null;
        }
    }

    public static void main(String[] args) throws Exception {
        CameraServiceImpl cameraClient = new CameraServiceImpl();
        try {
            List<String> frames = cameraClient.getFrames();
            System.out.println("Captured frames: " + frames);
        } finally {
            cameraClient.shutdownPythonProcess();
        }
    }

}

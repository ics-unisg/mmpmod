package ch.unisg.mmpmod.ambiguityresolver;

import ch.unisg.mmpmod.ambiguityresolver.camera.CameraService;
import ch.unisg.mmpmod.ambiguityresolver.ml.MLService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Service
public class AmbiguityResolverServiceImpl implements AmbiguityResolverService {
    private static final Logger logger = LoggerFactory.getLogger(AmbiguityResolverServiceImpl.class);
    private final CameraService cameraClient;
    private final MLService mlClient;

    public AmbiguityResolverServiceImpl(CameraService cameraClient, MLService mlClient) {
        this.cameraClient = cameraClient;
        this.mlClient = mlClient;
    }

    public String resolveAmbiguity(String windowId) {
        try {
            long timestamp = System.currentTimeMillis();
            // trigger the camera
            List<String> image_paths = cameraClient.getFrames();
            long latency = System.currentTimeMillis() - timestamp;
            logPerformance(windowId, "FrameCapture", latency);
            // forward images to the ML module for analysis
            timestamp = System.currentTimeMillis();
            String mlOutput = mlClient.analyzeFrames(image_paths);
            latency = System.currentTimeMillis() - timestamp;
            logPerformance(windowId, "MLInference", latency);
            return mlOutput;
        } catch (Exception e) {
            logger.error("Error while resolving ambiguity: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    public static void logPerformance(String windowId, String component, long latency) {
        try (FileWriter fw = new FileWriter("latencies.csv", true)) {
            fw.write(windowId + "," + component + "," + latency + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

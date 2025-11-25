package ch.unisg.mmpmod.processeventrouter;

import ch.unisg.mmpmod.ambiguityresolver.AmbiguityResolverService;
import ch.unisg.mmpmod.processeventemitter.EmitterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;

@Service
public class RouterServiceImpl implements RouterService {

    private static final Logger logger = LoggerFactory.getLogger(RouterServiceImpl.class);

    private final AmbiguityResolverService ambiguityResolverService;
    private final EmitterService emitterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RouterServiceImpl(AmbiguityResolverService ambiguityResolverService, EmitterService emitterService) {
        this.ambiguityResolverService = ambiguityResolverService;
        this.emitterService = emitterService;
    }

    public void resolveAmbiguityAndPublishEvent(String json_events, String windowId) {
        try {
            String ambiguityResolverOutput = ambiguityResolverService.resolveAmbiguity(windowId);

            if (ambiguityResolverOutput != null) {
                String resolved = objectMapper.readTree(ambiguityResolverOutput).get("resolved_ambiguity").toString();
                switch (resolved) {
                    case "true":
                        logger.info("Ambiguity resolved by ML module.");
                        String activity = objectMapper.readTree(ambiguityResolverOutput).get("activity").toString();
                        String conf = objectMapper.readTree(ambiguityResolverOutput).get("confidence").toString();
                        logConfidence(windowId, activity, conf);
                        long timestamp = System.currentTimeMillis();
                        emitterService.publishResolvedAmbiguousEvent(activity, json_events);
                        long latency = System.currentTimeMillis() - timestamp;
                        logPerformance(windowId, "PublishResolvedAmbiguity", latency);
                        break;
                    case "false":
                        logger.info("Ambiguity NOT resolved by ML module for " + json_events);
                        long timestampUnres = System.currentTimeMillis();
                        emitterService.publishUnresolvedAmbiguousEvents(json_events);
                        long latencyUnres = System.currentTimeMillis() - timestampUnres;
                        logPerformance(windowId, "PublishUnresolvedAmbiguity", latencyUnres);
                        break;
                }
            } else {
                logger.error("ML output is null, cannot resolve ambiguity.");
            }
        } catch (Exception e) {
            logger.error("Error while resolving ambiguity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishUnambiguousEvent(String jsonEvent, String windowId) {
        try {
            long timestamp = System.currentTimeMillis();
            emitterService.publishUnambiguousEvent(jsonEvent);
            long latency = System.currentTimeMillis() - timestamp;
            logPerformance(windowId, "PublishUnambiguous", latency);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void logPerformance(String windowId, String component, long latency) {
        try (FileWriter fw = new FileWriter("latencies.csv", true)) {
            fw.write(windowId + "," + component + "," + latency + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logConfidence(String windowId, String activity, String confidence) {
        try (FileWriter fw = new FileWriter("confidence.csv", true)) {
            fw.write(windowId + "," + activity + "," + confidence + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

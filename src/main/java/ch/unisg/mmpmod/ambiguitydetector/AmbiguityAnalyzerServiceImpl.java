package ch.unisg.mmpmod.ambiguitydetector;

import ch.unisg.mmpmod.processeventrouter.RouterService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AmbiguityAnalyzerServiceImpl implements AmbiguityAnalyzerService {

    private final long windowSize = 1000;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ambiguity-window");
                t.setDaemon(true);
                return t;
            });

    private final Object lock = new Object();
    private final List<ProcessEvent> buffer = new ArrayList<>();
    private ScheduledFuture<?> pendingClose = null;

    RouterService routerService;

    @Autowired
    public AmbiguityAnalyzerServiceImpl(RouterService routerService) {
        this.routerService = routerService;
    }

    public void cacheProcessEvent(ProcessEvent msg) {
        synchronized (lock) {
            buffer.add(msg);
            // (Re)schedule close
            if (pendingClose != null && !pendingClose.isDone()) {
                pendingClose.cancel(false);
            }
            pendingClose = scheduler.schedule(this::closeWindowSafely, windowSize, TimeUnit.MILLISECONDS);
        }
    }

    private void closeWindowSafely() {
        try {
            List<ProcessEvent> snapshot;
            synchronized (lock) {
                if (buffer.isEmpty()) return;
                snapshot = new ArrayList<>(buffer);
                buffer.clear();
                pendingClose = null;
            }
            onWindowClosed(snapshot);
        } catch (Exception e) {
            System.err.println("Window close error: " + e.getMessage());
        }
    }

    private void onWindowClosed(List<ProcessEvent> records) {
        if (records.size() == 1) {
            handleSingleEvent(records.get(0));
        } else {
            handleMultipleEvents(records);
        }
    }

    protected void handleSingleEvent(ProcessEvent pe) {
        String windowId = "ID"+ pe.receivedAt().toString();
        long latency = windowSize;
        logPerformance(windowId, "WindowingU", latency);
        System.out.printf("[UNAMBIGUOUS] Single event in window");
        StringBuilder event = new StringBuilder("{\"events\": ");
        event.append("{")
                .append("\"label\": \"").append(escapeJson(pe.label())).append("\",")
                .append("\"timestamp\": \"").append(pe.receivedAt()).append("\"")
                .append("}");
        event.append("}");
        routerService.publishUnambiguousEvent(event.toString(), windowId);
    }

    protected void handleMultipleEvents(List<ProcessEvent> list) {
        String windowID = "ID"+ list.get(0).receivedAt().toString();
        System.out.printf("[AMBIGUOUS] %d events in window. First at %s, last at %s%n",
                list.size(), list.get(0).receivedAt(), list.get(list.size() - 1).receivedAt());
        long latency = list.get(0).receivedAt().toEpochMilli() - list.get(list.size() - 1).receivedAt().toEpochMilli() + windowSize;
        logPerformance(windowID, "WindowingA", latency);
        StringBuilder events = new StringBuilder("{\"events\": [");
        for (int i = 0; i < list.size(); i++) {
            ProcessEvent pe = list.get(i);
            events.append("{")
                    .append("\"label\": \"").append(escapeJson(pe.label())).append("\",")
                    .append("\"timestamp\": \"").append(pe.receivedAt()).append("\"")
                    .append("}");

            if (i < list.size() - 1) {
                events.append(",");
            }
        }
        events.append("]}");
        routerService.resolveAmbiguityAndPublishEvent(events.toString(), windowID);
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @PreDestroy
    public void shutdown() {
        closeWindowSafely();
        scheduler.shutdown();
    }

    public static void logPerformance(String windowId, String component, long latency) {
        try (FileWriter fw = new FileWriter("latencies.csv", true)) {
            fw.write(windowId + "," + component + "," + latency + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

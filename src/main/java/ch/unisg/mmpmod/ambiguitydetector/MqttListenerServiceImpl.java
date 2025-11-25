package ch.unisg.mmpmod.ambiguitydetector;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MqttListenerServiceImpl implements MqttCallback {

    private final MqttClient client;
    private final MqttConnectOptions options;
    private final MqttProperties props;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AmbiguityAnalyzerService ambiguityAnalyzerService;
    private final String expectedPrefix = "{\"event\":{\"lifecycle:transition\":\"complete\"";

    public MqttListenerServiceImpl(MqttClient client, MqttConnectOptions options, MqttProperties props, AmbiguityAnalyzerServiceImpl ambiguityDetectionService) {
        this.client = client;
        this.options = options;
        this.props = props;
        this.ambiguityAnalyzerService = ambiguityDetectionService;
    }

    @PostConstruct
    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            client.setCallback(this);
            connectAndSubscribe();
        }
    }

    private void connectAndSubscribe() throws MqttException {
        if (!client.isConnected()) {
            client.connect(options);
        }
        client.subscribe(props.getTopic(), props.getQos());
        System.out.printf("Connected and subscribed to %s (QoS %d)%n", props.getTopic(), props.getQos());
    }

    @PreDestroy
    public void stop() {
        try {
            if (client.isConnected()) client.disconnect();
        } catch (Exception ignored) {}
        try {
            client.close();
        } catch (Exception ignored) {}
        System.out.println("MQTT client closed.");
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("connectionLost: " + (cause == null ? "(unknown)" : cause.getMessage()));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        if (payload.startsWith(expectedPrefix)) {
            int lastSlash = topic.lastIndexOf('/');
            String processEvent = (lastSlash >= 0) ? topic.substring(lastSlash + 1) : topic;
            System.out.println("[DETECTED] " + processEvent);
            ambiguityAnalyzerService.cacheProcessEvent(new ProcessEvent(processEvent, payload, message.getQos(), Instant.now()));
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        System.out.println("deliveryComplete: " + token.isComplete());
    }
}

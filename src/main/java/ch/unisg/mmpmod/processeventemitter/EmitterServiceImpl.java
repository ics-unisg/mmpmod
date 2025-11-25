package ch.unisg.mmpmod.processeventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class EmitterServiceImpl implements EmitterService {
    private static final Logger logger = LoggerFactory.getLogger(EmitterServiceImpl.class);

    private final MqttService mqttService;
    private final JsonToXesMapper jsonToXesMapper;
    private final ObjectMapper objectMapper;

    private final String topicProcessEvents;

    public EmitterServiceImpl(MqttService mqttService,
                              JsonToXesMapper jsonToXesMapper,
                              ObjectMapper objectMapper,
                              ConfigLoader config) {
        this.mqttService = mqttService;
        this.jsonToXesMapper = jsonToXesMapper;
        this.objectMapper = objectMapper;
        this.topicProcessEvents = config.get("publisher.topicProcessEvents");

    }

    @Override
    public void publishResolvedAmbiguousEvent(String activity, String ambiguousJsonEvents) {

        try {
            JsonNode eventsRoot = objectMapper.readTree(ambiguousJsonEvents);
            ArrayNode ambiguousEvents = (ArrayNode) eventsRoot.get("events");

            logger.info("Ambiguity resolved. Top class: " + activity);
            ObjectNode resolvedEvent = createResolvedEvent(activity, ambiguousEvents);
            String xesEvent = jsonToXesMapper.convertJsonToXes(resolvedEvent.toString());

            mqttService.publish(topicProcessEvents, xesEvent);
            xesEvent+="\n";
            FileOutputStream fos = new FileOutputStream("log.xes", true);
            fos.write(xesEvent.getBytes());

        } catch (Exception e) {
            logger.error("Error processing ML output: " + e.getMessage());
            throw new RuntimeException("Failed to process ML output", e);
        }
    }

    @Override
    public void publishUnresolvedAmbiguousEvents(String ambiguousEvents) {
        try{
            JsonNode eventsRoot = objectMapper.readTree(ambiguousEvents);
            ArrayNode ambiguousEventsArray = (ArrayNode) eventsRoot.get("events");

            handleUnresolvedAmbiguity(ambiguousEventsArray);

            logger.info("Ambiguity not resolved. Manual intervention needed for events flagged as ambiguous.");

        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private void handleUnresolvedAmbiguity(ArrayNode ambiguousEvents) {
        logger.info("Publishing ambiguous events with ambiguity flag to events label: " + topicProcessEvents);
        ambiguousEvents.forEach(event -> {
            logger.info(event.toString());
            if (event instanceof ObjectNode) {
                ((ObjectNode) event).put("ambiguous", true);
                try {
                    String xesEvent = jsonToXesMapper.convertJsonToXes(event.toString());
                    mqttService.publish(topicProcessEvents, xesEvent);
                    xesEvent+="\n";
                    FileOutputStream fos = new FileOutputStream("log.xes", true);
                    fos.write(xesEvent.getBytes());
                } catch (ParserConfigurationException | JsonProcessingException | TransformerException e) {
                    logger.error("Error converting ambiguous event to XES: " + e.getMessage());
                    throw new RuntimeException(e);
                } catch (MqttException e) {
                    logger.error("Error publishing ambiguous event to MQTT: " + e.getMessage());
                    throw new RuntimeException(e);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private ObjectNode createResolvedEvent(String topClass, ArrayNode ambiguousEvents) {
        ObjectNode resolvedEvent = objectMapper.createObjectNode();
        resolvedEvent.put("label", topClass.substring(1, topClass.length()-1));

        JsonNode firstEvent = ambiguousEvents.get(0);
        firstEvent.fields().forEachRemaining(entry -> {
            if (!entry.getKey().equals("label")) {
                resolvedEvent.set(entry.getKey(), entry.getValue());
            }
        });
        resolvedEvent.put("ambiguous", false);
        return resolvedEvent;
    }

    @Override
    public void publishUnambiguousEvent(String unambEvent) throws MqttException {

        try {
            // get the unambiguous event
            JsonNode root = objectMapper.readTree(unambEvent);
            JsonNode eventsNode = root.get("events");
            String eventsAsString = eventsNode.toString();

            // convert the JSON event to XES format
            String xesEvent = jsonToXesMapper.convertJsonToXes(eventsAsString);
            logger.info("Publishing unambiguous event: " + xesEvent);
            mqttService.publish(topicProcessEvents, xesEvent);
            FileOutputStream fos = new FileOutputStream("log.xes", true);
            xesEvent+="\n";
            fos.write(xesEvent.getBytes());
        } catch (Exception e) {
            logger.error("Error publishing unambiguous event: " + e.getMessage());
            throw new RuntimeException("Failed to publish unambiguous event", e);
        }
    }
}
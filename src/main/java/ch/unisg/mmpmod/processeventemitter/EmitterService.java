package ch.unisg.mmpmod.processeventemitter;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface EmitterService {
    void publishResolvedAmbiguousEvent(String mlOutput, String originalJsonEvents) throws MqttException;
    void publishUnambiguousEvent(String message) throws MqttException;
    void publishUnresolvedAmbiguousEvents(String ambiguousEvents);
}

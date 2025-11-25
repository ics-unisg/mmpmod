package ch.unisg.mmpmod.processeventemitter;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface MqttService {
    void publish (String topic, String message) throws MqttException;
    void connect() throws MqttException;
    void disconnect() throws MqttException;
    boolean isConnected();
}

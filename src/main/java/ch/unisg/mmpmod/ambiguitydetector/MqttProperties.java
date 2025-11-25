package ch.unisg.mmpmod.ambiguitydetector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    private String broker;
    private String clientId;
    private String topic;
    private int qos = 1;
    private boolean cleanSession = false;
    private int keepAlive = 60;
    private boolean automaticReconnect = true;
    private String username;
    private String password;

    // getters & setters
    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
    public boolean isCleanSession() { return cleanSession; }
    public void setCleanSession(boolean cleanSession) { this.cleanSession = cleanSession; }
    public int getKeepAlive() { return keepAlive; }
    public void setKeepAlive(int keepAlive) { this.keepAlive = keepAlive; }
    public boolean isAutomaticReconnect() { return automaticReconnect; }
    public void setAutomaticReconnect(boolean automaticReconnect) { this.automaticReconnect = automaticReconnect; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

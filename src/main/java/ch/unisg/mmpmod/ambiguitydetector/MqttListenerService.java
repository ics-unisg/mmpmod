package ch.unisg.mmpmod.ambiguitydetector;

public interface MqttListenerService {

    void start() throws Exception;

    void stop();
}

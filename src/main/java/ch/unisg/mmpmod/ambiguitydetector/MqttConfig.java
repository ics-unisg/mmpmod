package ch.unisg.mmpmod.ambiguitydetector;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfig {

    @Bean
    public MemoryPersistence memoryPersistence() {
        return new MemoryPersistence();
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties props) {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(props.isCleanSession());
        opts.setAutomaticReconnect(props.isAutomaticReconnect());
        opts.setKeepAliveInterval(props.getKeepAlive());
        if (props.getUsername() != null) {
            opts.setUserName(props.getUsername());
        }
        if (props.getPassword() != null) {
            opts.setPassword(props.getPassword().toCharArray());
        }
        return opts;
    }

    @Bean(destroyMethod = "close")
    public MqttClient mqttClient(MqttProperties props, MemoryPersistence persistence) throws Exception {
        return new MqttClient(props.getBroker(), props.getClientId(), persistence);
    }
}

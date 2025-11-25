package ch.unisg.mmpmod.processeventemitter;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Component
public class ConfigLoader {
    private final Properties properties = new Properties();

    public ConfigLoader() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find configuration file: application.properties");
            }
            properties.load(input);
            System.out.println("Loaded properties Publisher: " + properties);
        } catch (IOException ex) {
            throw new RuntimeException("Error loading configuration file: " + ex.getMessage(), ex);
        }
    }

    public String get(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config value for key: " + key);
        }
        return value;
    }
}

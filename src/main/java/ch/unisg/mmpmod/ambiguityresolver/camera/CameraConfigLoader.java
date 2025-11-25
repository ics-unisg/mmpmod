package ch.unisg.mmpmod.ambiguityresolver.camera;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * ConfigLoader is a utility class to load configuration properties from a file.
 * It uses the Java Properties class to read key-value pairs from a specified file.
 */

@Component
public class CameraConfigLoader {
    private final Properties properties = new Properties();

    public CameraConfigLoader() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find configuration file: application.properties");
            }
            properties.load(input);
            System.out.println("Loaded properties Camera: " + properties);
        } catch (IOException ex) {
            throw new RuntimeException("Error loading configuration file: " + ex.getMessage(), ex);
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }
}

package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    public static Properties loadConfig() throws IOException {
        Properties config = new Properties();
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IOException("Cannot find 'config.properties' in resources.");
            }
            config.load(input);
        }
        return config;
    }
}
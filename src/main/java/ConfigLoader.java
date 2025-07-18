/**
 * Medical Term Extraction Pipeline
 * Copyright (C) Roger Ward, 2025
 * DOI: 10.5281/zenodo.15960200
 *
 * This project is licensed under the MIT License.
 * See the LICENSE file in the project root for full license information.
 */
package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    public static String loadResourceAsString(String resourceName) throws IOException {
        try (InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Cannot find '" + resourceName + "' in resources.");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
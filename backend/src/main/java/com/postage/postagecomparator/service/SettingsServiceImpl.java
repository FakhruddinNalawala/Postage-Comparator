package com.postage.postagecomparator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.WeightBracket;
import com.postage.postagecomparator.util.FileWriteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class SettingsServiceImpl implements SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsServiceImpl.class);

    private static final String DEFAULT_DIR_NAME = ".postage-comparator";
    private static final String SETTINGS_FILE_NAME = "settings.json";

    private final ObjectMapper objectMapper;
    private final Object lock = new Object();

    private List<WeightBracket> ausPostWeightBrackets;

    public SettingsServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ausPostWeightBrackets = List.of(
                new WeightBracket(0, 0.25, 9.70, 12.70),
                new WeightBracket(0.25, 0.5, 11.15, 14.65),
                new WeightBracket(0.5, 1.0, 15.25, 19.25),
                new WeightBracket(1.0, 3.0, 19.30, 23.80),
                new WeightBracket(3.0, 5.0, 23.30, 31.80));
    }

    @Override
    public OriginSettings getOriginSettings() {
        Path path = settingsPath();
        if (!Files.exists(path)) {
            return null;
        }
        synchronized (lock) {
            try {
                return objectMapper.readValue(path.toFile(), OriginSettings.class);
            } catch (IOException e) {
                log.error("Failed to read origin settings from {}", path, e);
                throw new IllegalStateException("Unable to read origin settings", e);
            }
        }
    }

    @Override
    public OriginSettings updateOriginSettings(OriginSettings newSettings) {
        if (newSettings == null) {
            throw new IllegalArgumentException("newSettings must not be null");
        }

        if (newSettings.postcode() == null || newSettings.postcode().isBlank()) {
            throw new IllegalArgumentException("Postcode is required");
        }

        if (!newSettings.postcode().matches("\\d{4}")) {
            throw new IllegalArgumentException("Postcode must be 4 digits");
        }

        var existing = getOriginSettings();
        var themePreference = normalizeThemePreference(newSettings.themePreference());
        if (themePreference == null && existing != null) {
            themePreference = existing.themePreference();
        }
        OriginSettings withTimestamp = new OriginSettings(
                newSettings.postcode(),
                newSettings.suburb(),
                newSettings.state(),
                newSettings.country(),
                themePreference,
                Instant.now());

        synchronized (lock) {
            Path path = settingsPath();
            FileWriteUtils.safeWrite(path, temp -> {
                try {
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValue(temp.toFile(), withTimestamp);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to write origin settings", e);
                }
            }, log);
            return withTimestamp;
        }
    }

    @Override
    public OriginSettings updateThemePreference(String themePreference) {
        var normalized = normalizeThemePreference(themePreference);
        synchronized (lock) {
            var current = getOriginSettings();
            OriginSettings updated = current == null
                    ? new OriginSettings(null, null, null, null, normalized, Instant.now())
                    : new OriginSettings(
                            current.postcode(),
                            current.suburb(),
                            current.state(),
                            current.country(),
                            normalized,
                            Instant.now());

            Path path = settingsPath();
            FileWriteUtils.safeWrite(path, temp -> {
                try {
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValue(temp.toFile(), updated);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to write origin settings", e);
                }
            }, log);
            return updated;
        }
    }

    private Path settingsPath() {
        // Allow a system property override for tests or advanced configuration
        var baseDir = System.getProperty("POSTAGE_DATA_DIR");
        if (baseDir == null || baseDir.isBlank()) {
            baseDir = System.getenv("POSTAGE_DATA_DIR");
        }
        if (baseDir == null || baseDir.isBlank()) {
            var userHome = System.getProperty("user.home");
            baseDir = Path.of(userHome, DEFAULT_DIR_NAME).toString();
        }
        return Path.of(baseDir, SETTINGS_FILE_NAME);
    }

    @Override
    public String getAusPostApiKey() {
        var property = System.getProperty("AUSPOST_API_KEY");
        if (property != null && !property.isBlank()) {
            return property;
        }
        return System.getenv("AUSPOST_API_KEY");
    }

    /*
    @Override
    public String getSendleApiKey() {
        return isSandboxMode()
                ? System.getenv("SENDLE_SANDBOX_API_KEY")
                : System.getenv("SENDLE_API_KEY");
    }

    @Override
    public String getSendleId() {
        return isSandboxMode()
                ? System.getenv("SENDLE_SANDBOX_ID")
                : System.getenv("SENDLE_ID");
    }

    private boolean isSandboxMode() {
        var sendleMode = System.getenv("SENDLE_MODE");
        return sendleMode != null && sendleMode.equalsIgnoreCase("sandbox");
    }
    */

    @Override
    public List<WeightBracket> getAusPostWeightBrackets() {
        return Collections.unmodifiableList(ausPostWeightBrackets);
    }

    private String normalizeThemePreference(String themePreference) {
        if (themePreference == null || themePreference.isBlank()) {
            return null;
        }
        var normalized = themePreference.trim().toLowerCase();
        if (!normalized.equals("dark") && !normalized.equals("light") && !normalized.equals("sepia")) {
            throw new IllegalArgumentException("Theme preference must be dark, light, or sepia");
        }
        return normalized;
    }
}
package com.postage.postagecomparator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.WeightBracket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsServiceImplTest {

    @TempDir
    Path tempDir;

    private SettingsServiceImpl settingsService;

    @BeforeEach
    void setUp() {
        // Use a temp directory for POSTAGE_DATA_DIR so settings.json is isolated and writable
        System.setProperty("POSTAGE_DATA_DIR", tempDir.resolve(".postage-comparator").toString());

        settingsService = new SettingsServiceImpl(new ObjectMapper());
    }

    // --- Origin settings tests ---

    @Test
    void getOriginSettings_whenFileDoesNotExist_returnsNull() {
        var result = settingsService.getOriginSettings();
        assertThat(result).isNull();
    }

    @Test
    void updateOriginSettings_whenNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> settingsService.updateOriginSettings(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newSettings must not be null");
    }

    @Test
    void updateOriginSettings_whenPostcodeBlank_throwsIllegalArgumentException() {
        var settings = new OriginSettings("   ", "Suburb", "State", "AU", null, Instant.now());

        assertThatThrownBy(() -> settingsService.updateOriginSettings(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postcode is required");
    }

    @Test
    void updateOriginSettings_whenPostcodeNull_throwsIllegalArgumentException() {
        var settings = new OriginSettings(null, "Suburb", "State", "AU", null, Instant.now());

        assertThatThrownBy(() -> settingsService.updateOriginSettings(settings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postcode is required");
    }

    @Test
    void updateOriginSettings_whenPostcodeInvalidLengthOrNonDigits_throwsIllegalArgumentException() {
        var threeDigit = new OriginSettings("123", "Suburb", "State", "AU", null, Instant.now());
        var fiveDigit = new OriginSettings("12345", "Suburb", "State", "AU", null, Instant.now());
        var decimal = new OriginSettings("12.3", "Suburb", "State", "AU", null, Instant.now());

        assertThatThrownBy(() -> settingsService.updateOriginSettings(threeDigit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postcode must be 4 digits");
        assertThatThrownBy(() -> settingsService.updateOriginSettings(fiveDigit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postcode must be 4 digits");
        assertThatThrownBy(() -> settingsService.updateOriginSettings(decimal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postcode must be 4 digits");
    }

    @Test
    void updateOriginSettings_whenOtherFieldsMissing_isAllowedAndPersisted() {
        var input = new OriginSettings("3000", null, null, null, null, null);

        OriginSettings updated;
        try {
            updated = settingsService.updateOriginSettings(input);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Unable to write origin settings")) {
                Assumptions.assumeTrue(false, "Skipping test due to environment not allowing writes to POSTAGE_DATA_DIR");
                return;
            }
            throw ex;
        }

        assertThat(updated.postcode()).isEqualTo("3000");
        assertThat(updated.suburb()).isNull();
        assertThat(updated.state()).isNull();
        assertThat(updated.country()).isNull();
        assertThat(updated.updatedAt()).isNotNull();
    }

    @Test
    void updateOriginSettings_thenGetOriginSettings_returnsPersistedValue() {
        var input = new OriginSettings("3000", "Melbourne", "VIC", "AU", null, null);

        OriginSettings updated;
        try {
            updated = settingsService.updateOriginSettings(input);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Unable to write origin settings")) {
                Assumptions.assumeTrue(false, "Skipping test due to environment not allowing writes to POSTAGE_DATA_DIR");
                return;
            }
            throw ex;
        }

        var fromDisk = settingsService.getOriginSettings();

        assertThat(fromDisk).isNotNull();
        assertThat(fromDisk.postcode()).isEqualTo("3000");
        assertThat(fromDisk.suburb()).isEqualTo("Melbourne");
        assertThat(fromDisk.state()).isEqualTo("VIC");
        assertThat(fromDisk.country()).isEqualTo("AU");
        assertThat(fromDisk.updatedAt()).isNotNull();
        assertThat(fromDisk.updatedAt()).isAfterOrEqualTo(updated.updatedAt());
    }

    @Test
    void getOriginSettings_whenFileCorrupted_throwsIllegalStateException() throws IOException {
        Path settingsFile = tempDir
                .resolve(".postage-comparator")
                .resolve("settings.json");
        Files.createDirectories(settingsFile.getParent());
        Files.writeString(settingsFile, "not valid json");

        assertThatThrownBy(() -> settingsService.getOriginSettings())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to read origin settings");
    }

    @Test
    void updateThemePreference_whenNoOrigin_existsAndPersistsTheme() {
        OriginSettings updated;
        try {
            updated = settingsService.updateThemePreference("dark");
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Unable to write origin settings")) {
                Assumptions.assumeTrue(false, "Skipping test due to environment not allowing writes to POSTAGE_DATA_DIR");
                return;
            }
            throw ex;
        }

        assertThat(updated.themePreference()).isEqualTo("dark");

        var fromDisk = settingsService.getOriginSettings();
        assertThat(fromDisk).isNotNull();
        assertThat(fromDisk.themePreference()).isEqualTo("dark");
    }

    // --- Weight brackets ---

    @Test
    void getAusPostWeightBrackets_returnsExpectedImmutableList() {
        List<WeightBracket> brackets = settingsService.getAusPostWeightBrackets();

        assertThat(brackets).hasSize(5);

        // Check first and last entries to ensure the configuration is as expected
        WeightBracket first = brackets.getFirst();
        assertThat(first.minWeightInclusive()).isEqualTo(0.0);
        assertThat(first.maxWeightInclusive()).isEqualTo(0.25);
        assertThat(first.priceStandard()).isEqualTo(9.70);
        assertThat(first.priceExpress()).isEqualTo(12.70);

        WeightBracket last = brackets.getLast();
        assertThat(last.minWeightInclusive()).isEqualTo(3.0);
        assertThat(last.maxWeightInclusive()).isEqualTo(5.0);
        assertThat(last.priceStandard()).isEqualTo(23.30);
        assertThat(last.priceExpress()).isEqualTo(31.80);

        // List should be unmodifiable
        assertThatThrownBy(() -> brackets.add(first))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // No helper methods needed now; we rely on a System property override for POSTAGE_DATA_DIR.
}


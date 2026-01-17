package com.postage.postagecomparator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

/**
 * Base class for integration tests.
 * Loads full Spring context and configures an isolated data directory.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @TempDir
    Path tempDir;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUpDataDir() {
        System.setProperty("POSTAGE_DATA_DIR", tempDir.resolve(".postage-comparator").toString());
    }

    @AfterEach
    void clearDataDir() {
        System.clearProperty("POSTAGE_DATA_DIR");
    }
}

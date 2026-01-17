package com.postage.postagecomparator.integration;

import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.ThemePreferenceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsIntegrationTest extends IntegrationTestBase {

    @Test
    void getOrigin_whenMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/settings/origin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void putOrigin_thenGetOrigin_returnsPersistedValue() throws Exception {
        var request = new OriginSettings("3000", "Melbourne", "VIC", "AU", null, null);

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.postcode").value("3000"))
                .andExpect(jsonPath("$.state").value("VIC"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/settings/origin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.postcode").value("3000"))
                .andExpect(jsonPath("$.suburb").value("Melbourne"));
    }

    @Test
    void putOrigin_whenInvalidPostcode_returns400() throws Exception {
        var request = new OriginSettings("ABC", "Sydney", "NSW", "AU", null, Instant.now());

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void putTheme_thenGetOrigin_returnsThemePreference() throws Exception {
        var request = new ThemePreferenceRequest("light");

        mockMvc.perform(put("/api/settings/theme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.themePreference").value("light"));

        mockMvc.perform(get("/api/settings/origin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.themePreference").value("light"));
    }
}

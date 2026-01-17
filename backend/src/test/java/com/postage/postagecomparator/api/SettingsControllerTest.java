package com.postage.postagecomparator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.ThemePreferenceRequest;
import com.postage.postagecomparator.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SettingsController.class)
@Import(GlobalExceptionHandler.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettingsService settingsService;

    @Test
    void getOrigin_whenMissing_returns404() throws Exception {
        given(settingsService.getOriginSettings()).willReturn(null);

        mockMvc.perform(get("/api/settings/origin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrigin_whenExists_returns200() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        given(settingsService.getOriginSettings()).willReturn(origin);

        mockMvc.perform(get("/api/settings/origin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.postcode").value("2000"))
                .andExpect(jsonPath("$.suburb").value("Sydney"));
    }

    @Test
    void updateOrigin_whenValid_returns200() throws Exception {
        var request = new OriginSettings("3000", "Melbourne", "VIC", "AU", null, null);
        var updated = new OriginSettings("3000", "Melbourne", "VIC", "AU", null, Instant.now());
        given(settingsService.updateOriginSettings(request)).willReturn(updated);

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.postcode").value("3000"))
                .andExpect(jsonPath("$.state").value("VIC"));
    }

    @Test
    void updateOrigin_whenPostcodeInvalid_returns400() throws Exception {
        var request = new OriginSettings("ABC", "Sydney", "NSW", "AU", null, null);

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Postcode must be 4 digits"));
    }

    @Test
    void updateOrigin_whenSuburbBlank_returns400() throws Exception {
        var request = new OriginSettings("2000", " ", "NSW", "AU", null, null);

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("must not be blank"));
    }

    @Test
    void updateTheme_whenValid_returns200() throws Exception {
        var request = new ThemePreferenceRequest("sepia");
        var updated = new OriginSettings("2000", "Sydney", "NSW", "AU", "sepia", Instant.now());
        given(settingsService.updateThemePreference("sepia")).willReturn(updated);

        mockMvc.perform(put("/api/settings/theme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.themePreference").value("sepia"));
    }

    @Test
    void updateTheme_whenInvalid_returns400() throws Exception {
        var request = new ThemePreferenceRequest("neon");

        mockMvc.perform(put("/api/settings/theme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Theme preference must be dark, light, or sepia"));
    }
}

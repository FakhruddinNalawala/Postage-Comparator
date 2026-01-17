package com.postage.postagecomparator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.model.*;
import com.postage.postagecomparator.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QuoteController.class)
@Import(GlobalExceptionHandler.class)
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QuoteService quoteService;

    @Test
    void createQuote_whenValid_returns200() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );

        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 1.0);
        var carrierQuote = new CarrierQuote("AUSPOST", "Parcel Post", 2, 4, 1.0, 10.0, 0.0, 11.0, "RULES", true, null);
        var result = new QuoteResult(
                1000,
                1.0,
                0.25,
                1000,
                origin,
                destination,
                packaging,
                List.of(carrierQuote),
                "AUD",
                Instant.now()
        );

        given(quoteService.calculateQuote(request)).willReturn(result);

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.carrierQuotes[0].carrier").value("AUSPOST"));
    }

    @Test
    void createQuote_whenItemsMissing_returns400() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(),
                "pack-1",
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("must not be empty"));
    }

    @Test
    void createQuote_whenItemQuantityInvalid_returns400() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 0)),
                "pack-1",
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("must be greater than 0"));
    }

    @Test
    void createQuote_whenCountryInvalid_returns400() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "A",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Country must be 2 letters"));
    }

    @Test
    void createQuote_whenServiceThrowsIllegalState_returns500() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );
        given(quoteService.calculateQuote(request))
                .willThrow(new IllegalStateException("Origin settings must be configured"));

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Origin settings must be configured"));
    }
}

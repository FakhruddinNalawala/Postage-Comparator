package com.postage.postagecomparator.integration;

import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.ShipmentItemSelection;
import com.postage.postagecomparator.model.ShipmentRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(QuotesIntegrationTest.WireMockConfig.class)
@org.springframework.test.context.TestPropertySource(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "providers.auspost.enabled=true"
        }
)
class QuotesIntegrationTest extends IntegrationTestBase {

    @AfterEach
    void clearAusPostKey() {
        System.clearProperty("AUSPOST_API_KEY");
    }

    @Test
    void quote_whenItemsEmpty_returns400() throws Exception {
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
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void quote_whenSpuriousItem_returns400() throws Exception {
        var packagingId = seedOriginAndPackaging();

        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("missing-item", 1)),
                packagingId,
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Item with id missing-item not found"));
    }

    @Test
    void quote_whenPackagingBlank_returns400() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                " ",
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("must not be blank"));
    }

    @Test
    void quote_whenPackagingMissing_returns400() throws Exception {
        var itemId = seedOriginAndItem();

        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection(itemId, 1)),
                "missing-pack",
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Packaging with id missing-pack not found"));
    }

    @Test
    void quote_whenOriginMissing_returns500() throws Exception {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void quote_whenDestinationInvalid_returns400() throws Exception {
        var request = new ShipmentRequest(
                "ABC",
                "Melbourne",
                "VIC",
                "AU",
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
                .andExpect(jsonPath("$.error.message").value("Destination postcode must be 4 digits"));
    }

    @Test
    void quote_whenValid_usesAusPostApiWhenKeyConfiguredOtherwiseRules() throws Exception {
        var ids = seedOriginItemAndPackaging();

        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection(ids.itemId, 1)),
                ids.packagingId,
                false
        );

        var result = mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        boolean hasAusPostKey = hasValue(System.getProperty("AUSPOST_API_KEY"))
                || hasValue(System.getenv("AUSPOST_API_KEY"));
        if (hasAusPostKey) {
            result.andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AUSPOST_API')]").isNotEmpty())
                    .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AUSPOST_API')].ruleFallbackUsed")
                            .value(false));
        } else {
            result.andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'RULES')]").isNotEmpty())
                    .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'RULES')].ruleFallbackUsed")
                            .value(true));
        }
    }

    @Test
    void quote_whenValidAndApiKey_usesAusPostApi() throws Exception {
        System.setProperty("AUSPOST_API_KEY", "test-key");
        var ids = seedOriginItemAndPackaging();

        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection(ids.itemId, 1)),
                ids.packagingId,
                false
        );

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AUSPOST_API')]").isNotEmpty())
                .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AUSPOST_API')].ruleFallbackUsed").value(false));
    }

    @Test
    void quote_whenItemQuantityInvalid_returns400() throws Exception {
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
    void quote_whenCountryInvalid_returns400() throws Exception {
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

    private String seedOriginAndItem() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var item = new Item(null, "Widget", "Small", 100);

        var created = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(origin)))
                .andExpect(status().isOk());
        return objectMapper.readValue(created, Item.class).id();
    }

    private String seedOriginAndPackaging() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        var created = mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(packaging)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(put("/api/settings/origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(origin)))
                .andExpect(status().isOk());
        return objectMapper.readValue(created, Packaging.class).id();
    }

    private SeededIds seedOriginItemAndPackaging() throws Exception {
        var itemId = seedOriginAndItem();
        var packaging = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        var created = mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(packaging)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var packagingId = objectMapper.readValue(created, Packaging.class).id();
        return new SeededIds(itemId, packagingId);
    }

    private record SeededIds(String itemId, String packagingId) {
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    @TestConfiguration
    static class WireMockConfig {
        @Bean(initMethod = "start", destroyMethod = "stop")
        public com.github.tomakehurst.wiremock.WireMockServer wireMockServer() {
            var server = new com.github.tomakehurst.wiremock.WireMockServer(0);
            server.stubFor(get(urlPathEqualTo("/postage/parcel/domestic/calculate.json"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "postage_result": {
                                        "total_cost": "12.50",
                                        "service": "Parcel Post",
                                        "delivery_time": "Delivered in 2-3 business days"
                                      }
                                    }
                                    """)));
            return server;
        }

        @Bean
        public WebClient ausPostWebClient(WebClient.Builder builder,
                                          com.github.tomakehurst.wiremock.WireMockServer wireMockServer) {
            return builder
                    .baseUrl(wireMockServer.baseUrl())
                    .build();
        }
    }
}

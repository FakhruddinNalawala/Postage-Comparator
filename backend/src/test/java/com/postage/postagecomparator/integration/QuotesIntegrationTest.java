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
import org.springframework.util.StringUtils;
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
        var request = buildRequest(List.of());

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void quote_whenSpuriousItem_returns400() throws Exception {
        seedOriginAndPackaging();

        var request = buildRequest(List.of(new ShipmentItemSelection("missing-item", 1)));

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Item with id missing-item not found"));
    }

    @Test
    void quote_whenNoPackagingAvailable_returns400() throws Exception {
        var itemId = seedOriginAndItem();

        var request = buildRequest(List.of(new ShipmentItemSelection(itemId, 1)));

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("No packaging available. Please create at least one packaging option."));
    }

    // @Test
    // void quote_whenOriginMissing_returns500() throws Exception {
    //     var request = buildRequest(List.of(new ShipmentItemSelection("item-1", 1)));
    //
    //     mockMvc.perform(post("/api/quotes")
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(objectMapper.writeValueAsString(request)))
    //             .andExpect(status().isInternalServerError())
    //             .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    //             .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    // }

    @Test
    void quote_whenDestinationInvalid_returns400() throws Exception {
        var request = buildRequest(
                "ABC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1))
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

        var request = buildRequest(List.of(new ShipmentItemSelection(ids.itemId, 1)));

        var result = mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        boolean hasAusPostKey = StringUtils.hasText(System.getProperty("AUSPOST_API_KEY"))
                || StringUtils.hasText(System.getenv("AUSPOST_API_KEY"));
        if (hasAusPostKey) {
            result.andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AusPost API')]").isNotEmpty())
                    .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AusPost API' && @.ruleFallbackUsed == false)]").isNotEmpty());
        } else {
            result.andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'RULES')]").isNotEmpty())
                    .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'RULES' && @.ruleFallbackUsed == true)]").isNotEmpty());
        }
    }

    @Test
    void quote_whenValidAndApiKey_usesAusPostApi() throws Exception {
        System.setProperty("AUSPOST_API_KEY", "test-key");
        var ids = seedOriginItemAndPackaging();

        var request = buildRequest(List.of(new ShipmentItemSelection(ids.itemId, 1)));

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AusPost API')]").isNotEmpty())
                .andExpect(jsonPath("$.carrierQuotes[?(@.pricingSource == 'AusPost API' && @.ruleFallbackUsed == false)]").isNotEmpty());
    }

    @Test
    void quote_whenItemQuantityInvalid_returns400() throws Exception {
        var request = buildRequest(List.of(new ShipmentItemSelection("item-1", 0)));

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
        var request = buildRequest(
                "3000",
                "A",
                List.of(new ShipmentItemSelection("item-1", 1))
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
        var item = new Item(null, "Widget", "Small", 100, 10, 20, 30);

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
        // Use a box large enough to fit the seeded item volume under the
        // new volume-based packaging rules.
        var packaging = new Packaging(null, "Box", "Small box", 30, 30, 30, 1.0);

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
        // Use a box large enough to fit the seeded item volume under the
        // new volume-based packaging rules.
        var packaging = new Packaging(null, "Box", "Small box", 30, 30, 30, 1.0);

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

    private ShipmentRequest buildRequest(List<ShipmentItemSelection> items) {
        return buildRequest("3000", "AU", items);
    }

    private ShipmentRequest buildRequest(String postcode,
                                         String country,
                                         List<ShipmentItemSelection> items) {
        return buildRequest(postcode, "Melbourne", "VIC", country, items);
    }

    private ShipmentRequest buildRequest(String postcode,
                                         String suburb,
                                         String state,
                                         String country,
                                         List<ShipmentItemSelection> items) {
        return new ShipmentRequest(
                postcode,
                suburb,
                state,
                country,
                items,
                null
        );
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

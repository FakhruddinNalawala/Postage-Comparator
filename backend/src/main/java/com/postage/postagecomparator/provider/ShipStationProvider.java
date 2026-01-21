package com.postage.postagecomparator.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import com.postage.postagecomparator.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ShipStation connector implementation of the CarrierProvider SPI.
 * Uses the /v2/rates/estimate endpoint to fetch multiple rate quotes.
 *
 * Docs: https://docs.shipstation.com/openapi/rates/estimate_rates
 */
@Component
public class ShipStationProvider implements CarrierProvider {

    private static final Logger log = LoggerFactory.getLogger(ShipStationProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> CARRIER_IDS = List.of(
            "se-4731463",
            "se-4731464",
            "se-4731516",
            "se-4731511");

    private final WebClient shipStationWebClient;
    private final SettingsService settingsService;
    private final QuoteRequestHelper requestHelper;

    public ShipStationProvider(@Qualifier("shipStationWebClient") WebClient shipStationWebClient,
            SettingsService settingsService,
            QuoteRequestHelper requestHelper) {
        this.shipStationWebClient = shipStationWebClient;
        this.settingsService = settingsService;
        this.requestHelper = requestHelper;
    }

    @Override
    public String getName() {
        return "shipstation";
    }

    @Override
    public boolean isEnabled(ProviderConfig config) {
        return config.getProvider(getName())
                .map(ProviderConfig.ProviderSettings::enabled)
                .orElse(false);
    }

    @Override
    public Optional<CarrierQuote> quote(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items) {
        Optional<List<CarrierQuote>> quotes = quotes(request, origin, packaging, items);
        return quotes.flatMap(list -> list.stream()
                .min(Comparator.comparingDouble(CarrierQuote::totalCostAud)));
    }

    @Override
    public Optional<List<CarrierQuote>> quotes(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items) {
        String apiKey = settingsService.getShipStationApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("ShipStation API key not configured, skipping API call and using rules-based pricing");
            return Optional.empty();
        }

        QuoteResult.Destination destination = requestHelper.buildDestination(request);
        int totalWeightGrams = requestHelper.calculateTotalWeight(request.items());

        String fromCity = sanitizeCity(origin.suburb(), origin.postcode());
        String toCity = sanitizeCity(destination.suburb(), destination.postcode());
        double weightGrams = Math.max(1.0, totalWeightGrams);

        Map<String, Object> weight = new java.util.HashMap<>();
        weight.put("value", weightGrams);
        weight.put("unit", "gram");

        Map<String, Object> dimensions = new java.util.HashMap<>();
        dimensions.put("length", packaging.lengthCm());
        dimensions.put("width", packaging.widthCm());
        dimensions.put("height", packaging.heightCm());
        dimensions.put("unit", "centimeter");

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("carrier_ids", CARRIER_IDS);
        requestBody.put("from_country_code", origin.country());
        requestBody.put("from_postal_code", origin.postcode());
        requestBody.put("from_city_locality", fromCity);
        requestBody.put("from_state_province", origin.state());
        requestBody.put("to_country_code", destination.country());
        requestBody.put("to_postal_code", destination.postcode());
        requestBody.put("to_city_locality", toCity);
        requestBody.put("to_state_province", destination.state());
        requestBody.put("weight", weight);
        requestBody.put("dimensions", dimensions);
        // requestBody.put("ship_date", shipDate);
        String uri = "/v2/rates/estimate";

        try {

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = shipStationWebClient
                    .post()
                    .uri(uri)
                    .header("api-key", apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (response == null) {
                log.warn("ShipStation API returned null response for rate estimate");
                return Optional.of(List.of());
            }

            return Optional.of(parseRatesResponse(response, packaging));
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("ShipStation API call failed (status: {}).\n Request: {}.\n Response: {}.\n Stack: {}",
                    e.getStatusCode(),
                    toJson(requestBody),
                    e.getResponseBodyAsString(),
                    summarizeStackTrace(e));
            log.info("ShipStation request uri: {}", uri);
            log.info("ShipStation request headers: {}", Map.of(
                    "api-key", maskApiKey(apiKey)));
            log.info("ShipStation request body: {}", requestBody);
            return Optional.empty();
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.error("ShipStation API network/client error: {}. Stack: {}", e.getMessage(), summarizeStackTrace(e));
            return Optional.empty();
        } catch (Exception e) {
            log.error("ShipStation API call failed: {}. Stack: {}", e.getMessage(), summarizeStackTrace(e));
            return Optional.empty();
        }
    }

    private List<CarrierQuote> parseRatesResponse(List<Map<String, Object>> response,
            Packaging packaging) {
        return response.stream()
                .map(rate -> {
                    Object shippingAmountObj = rate.get("shipping_amount");
                    String validationStatus = extractString(rate, "validation_status");
                    if ("invalid".equalsIgnoreCase(validationStatus)) {
                        return null;
                    }
                    String currency = null;
                    Object amountObj = null;
                    if (shippingAmountObj instanceof Map<?, ?> shippingAmountRaw) {
                        Object currencyObj = shippingAmountRaw.get("currency");
                        if (currencyObj != null) {
                            currency = currencyObj.toString();
                        }
                        amountObj = shippingAmountRaw.get("amount");
                    }
                    if (currency != null && !currency.equalsIgnoreCase("aud")) {
                        return null;
                    }
                    Double amount = parseDouble(amountObj);
                    if (amount == null || amount <= 0) {
                        return null;
                    }

                    String serviceName = extractString(rate, "service_code");
                    Integer deliveryDays = parseInteger(rate.get("delivery_days"));

                    double packagingCost = packaging.packagingCostAud();
                    double deliveryCost = amount - packagingCost;

                    return new CarrierQuote(
                            "SHIPSTATION",
                            serviceName != null ? serviceName : "rate",
                            deliveryDays,
                            deliveryDays,
                            packagingCost,
                            deliveryCost,
                            0.0,
                            amount,
                            "SHIPSTATION_API",
                            false,
                            null);
                })
                .filter(quote -> quote != null)
                .toList();
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String sanitizeCity(String city, String fallback) {
        if (city != null && !city.isBlank()) {
            return city;
        }
        return fallback != null ? fallback : "Unknown";
    }

    private String summarizeStackTrace(Throwable error) {
        StackTraceElement[] stack = error.getStackTrace();
        int limit = Math.min(stack.length, 10);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            builder.append(stack[i]).append(i == limit - 1 ? "" : " | ");
        }
        return builder.toString();
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "<empty>";
        }
        int visible = Math.min(4, apiKey.length());
        return apiKey.substring(0, visible) + "***";
    }
}

package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import com.postage.postagecomparator.service.SettingsService;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AusPostProvider implements CarrierProvider {

    // @SuppressWarnings("unused")
    private final WebClient ausPostWebClient;
    private final SettingsService settingsService;
    private static final Logger log = LoggerFactory.getLogger(AusPostProvider.class);
    private final QuoteRequestHelper requestHelper;

    public AusPostProvider(@Qualifier("ausPostWebClient") WebClient ausPostWebClient, SettingsService settingsService, QuoteRequestHelper requestHelper) {
        this.ausPostWebClient = ausPostWebClient;
        this.settingsService = settingsService;
        this.requestHelper = requestHelper;
    }

    @Override
    public String getName() {
        return "auspost";
    }

    @Override
    public boolean isEnabled(ProviderConfig config) {
        return config.getProvider(getName())
                .map(ProviderConfig.ProviderSettings::enabled)
                .orElse(false);
    }

    @Override
    public Optional<List<CarrierQuote>> quotes(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items) {
        return Optional.of(List.of());
    }

    @Override
    public Optional<CarrierQuote> quote(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items) {

        String apiKey = settingsService.getAusPostApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("AusPost API key not configured, skipping API call and using rules-based pricing");
            return Optional.empty(); // Return empty to allow fallback to rules
        }

        QuoteResult.Destination destination = requestHelper.buildDestination(request);
        int totalWeightGrams = requestHelper.calculateTotalWeight(request.items());
        boolean isExpress = request.isExpress();

        String uriAusPostCalculate = "/postage/parcel/domestic/calculate.json";
        String serviceCode = isExpress ? "AUS_PARCEL_EXPRESS" : "AUS_PARCEL_REGULAR";

        log.info("Attempting AusPost API call: from {} {} to {} {}, weight: {}g, service: {}",
                origin.postcode(), origin.suburb(),
                destination.postcode(), destination.suburb(),
                totalWeightGrams, serviceCode);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = ausPostWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(uriAusPostCalculate)
                            .queryParam("from_postcode", origin.postcode())
                            .queryParam("to_postcode", destination.postcode())
                            .queryParam("length", String.valueOf(packaging.lengthCm()))
                            .queryParam("width", String.valueOf(packaging.widthCm()))
                            .queryParam("height", String.valueOf(packaging.heightCm()))
                            .queryParam("weight", String.valueOf(totalWeightGrams / 1000.0))
                            .queryParam("service_code", serviceCode)
                            .build())
                    .header("AUTH-KEY", apiKey)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> {
                                log.error(
                                        "AusPost API returned 4xx client error (status: {}). Request: from {} {} to {} {}. Service: {}",
                                        clientResponse.statusCode(),
                                        origin.postcode(), origin.suburb(),
                                        destination.postcode(), destination.suburb(),
                                        serviceCode);
                                return Mono.error(new RuntimeException(
                                        "AusPost API client error: " + clientResponse.statusCode()));
                            })
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> {
                                log.error(
                                        "AusPost API returned 5xx server error (status: {}). Request: from {} {} to {} {}. Service: {}",
                                        clientResponse.statusCode(),
                                        origin.postcode(), origin.suburb(),
                                        destination.postcode(), destination.suburb(),
                                        serviceCode);
                                return Mono.error(new RuntimeException(
                                        "AusPost API server error: " + clientResponse.statusCode()));
                            })
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.error("AusPost API returned null response. Request: from {} {} to {} {}. Service: {}",
                        origin.postcode(), origin.suburb(),
                        destination.postcode(), destination.suburb(),
                        serviceCode);
                return Optional.empty();
            }

            // Parse response and map to CarrierQuote
            // AusPost API response structure: typically has "postage_result" with cost,
            // service, etc.
            log.debug("AusPost API call succeeded, parsing response");
            CarrierQuote quote = parseAusPostResponse(response, packaging, isExpress);
            if (quote != null) {
                log.info("AusPost API quote successfully retrieved: ${}", quote.totalCostAud());
            } else {
                log.warn("AusPost API response parsed to null, falling back to rules");
            }
            return Optional.ofNullable(quote);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error(
                    "AusPost API returned error response (status: {}). Request: from {} {} to {} {}. Service: {}. Error: {}",
                    e.getStatusCode(),
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    serviceCode,
                    e.getMessage(), e);
            return Optional.empty();
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.error("AusPost API network/client error. Request: from {} {} to {} {}. Service: {}. Error: {}",
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    serviceCode,
                    e.getMessage(), e);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error(
                    "AusPost API call failed with runtime error. Request: from {} {} to {} {}. Service: {}. Error: {}",
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    serviceCode,
                    e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error(
                    "AusPost API call failed with unexpected error. Request: from {} {} to {} {}. Service: {}. Error: {}",
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    serviceCode,
                    e.getMessage(), e);
            return Optional.empty(); // Return null to allow fallback to rules
        }
    }

    @SuppressWarnings("unchecked")
    private CarrierQuote parseAusPostResponse(Map<String, Object> response, Packaging packaging, boolean isExpress) {
        try {
            log.debug("Parsing AusPost API response: {}", response);
            Map<String, Object> postageResult = (Map<String, Object>) response.get("postage_result");
            if (postageResult == null) {
                log.error("AusPost response missing postage_result. Full response: {}", response);
                return null;
            }

            // Extract cost from "total_cost" (string format like "15.05")
            Object costObj = postageResult.get("total_cost");
            Double totalCost = costObj != null ? parseDouble(costObj) : null;

            if (totalCost == null) {
                log.error("AusPost response missing total_cost. postage_result: {}", postageResult);
                return null;
            }

            // Extract service name
            String serviceName = extractString(postageResult, "service");
            if (serviceName == null) {
                serviceName = isExpress ? "Express Post" : "Parcel Post";
            }

            // Extract delivery time from string format like "Delivered in 4 business days"
            // Try to parse the number(s) from the string
            String deliveryTimeStr = extractString(postageResult, "delivery_time");
            Integer etaMin = null;
            Integer etaMax = null;
            if (deliveryTimeStr != null) {
                Integer[] etaDays = parseDaysFromDeliveryTimeString(deliveryTimeStr);
                if (etaDays != null && etaDays.length == 2) {
                    etaMin = etaDays[0];
                    etaMax = etaDays[1];
                } else if (etaDays != null && etaDays.length == 1) {
                    etaMin = etaDays[0];
                    etaMax = etaDays[0];
                }
            }

            // Calculate delivery cost (total cost minus packaging cost)
            double packagingCostAud = packaging.packagingCostAud();
            double deliveryCost = totalCost - packagingCostAud;
            double surcharges = 0.0; // AusPost may include surcharges in total_cost or separately

            return new CarrierQuote(
                    "AUSPOST",
                    serviceName,
                    etaMin != null ? etaMin : (isExpress ? 1 : 2),
                    etaMax != null ? etaMax : (isExpress ? 3 : 6),
                    packagingCostAud,
                    deliveryCost,
                    surcharges,
                    totalCost,
                    "AUSPOST_API",
                    false, // ruleFallbackUsed
                    null // rawCarrierRef - could extract from response if available
            );
        } catch (ClassCastException e) {
            log.error("Failed to parse AusPost response: type casting error", e);
            return null;
        } catch (NullPointerException e) {
            log.error("Failed to parse AusPost response: null pointer exception", e);
            return null;
        } catch (NumberFormatException e) {
            log.error("Failed to parse AusPost response: number format error", e);
            return null;
        } catch (Exception e) {
            log.error("Failed to parse AusPost response: unexpected error", e);
            return null;
        }
    }

    /**
     * Parses the number of days from AusPost delivery time string.
     * Examples:
     * "Delivered in 4 business days" -> returns array with [4, 4]
     * "Delivered in 2-3 business days" -> returns array with [2, 3]
     * Returns null if parsing fails.
     */
    private Integer[] parseDaysFromDeliveryTimeString(String deliveryTimeStr) {
        if (deliveryTimeStr == null || deliveryTimeStr.isBlank()) {
            return null;
        }

        // First try to match range format: "Delivered in X-Y business days"
        java.util.regex.Pattern rangePattern = java.util.regex.Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
        java.util.regex.Matcher rangeMatcher = rangePattern.matcher(deliveryTimeStr);

        if (rangeMatcher.find()) {
            try {
                int min = Integer.parseInt(rangeMatcher.group(1));
                int max = Integer.parseInt(rangeMatcher.group(2));
                return new Integer[] { min, max };
            } catch (NumberFormatException e) {
                log.debug("Failed to parse range from delivery time string: {}", deliveryTimeStr);
            }
        }

        // Fallback to single number format: "Delivered in Z business days"
        java.util.regex.Pattern singlePattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher singleMatcher = singlePattern.matcher(deliveryTimeStr);

        if (singleMatcher.find()) {
            try {
                int days = Integer.parseInt(singleMatcher.group(1));
                return new Integer[] { days, days };
            } catch (NumberFormatException e) {
                log.debug("Failed to parse days from delivery time string: {}", deliveryTimeStr);
                return null;
            }
        }

        return null;
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

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
